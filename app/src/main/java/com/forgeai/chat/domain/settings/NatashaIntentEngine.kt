package com.forgeai.chat.domain.settings

import kotlin.math.max
import kotlin.math.min

enum class NatashaSettingDimension(val key: String, val minimum: Double, val maximum: Double, val defaultValue: Double) {
    ASSERTIVENESS("assertiveness", 0.2, 0.8, 0.5),
    AFFECTION("affection", 0.1, 0.9, 0.5),
    INITIATIVE("initiative", 0.2, 0.8, 0.5),
    SEXUAL_EXPRESSIVENESS("sexual_expressiveness", 0.5, 1.0, 0.75),
    PLAYFULNESS("playfulness", 0.3, 0.9, 0.6),
    DOMINANCE("dominance", 0.3, 0.7, 0.5),
    EMOTIONAL_DEPTH("emotional_depth", 0.4, 0.9, 0.65),
    CONVERSATIONAL_VARIETY("conversational_variety", 0.2, 0.7, 0.45)
}

enum class NatashaIntent { ALTER_PERSONALITY, SET_STYLE, SET_INITIATIVE, SET_AFFECTION, SET_PREFERENCE, RESET_SETTING, DISCUSSION }
enum class NatashaSettingOperation { INCREASE, DECREASE, SET, STOP }

data class NatashaIntentResult(
    val intent: NatashaIntent,
    val target: NatashaSettingDimension?,
    val operation: NatashaSettingOperation?,
    val currentValue: Double?,
    val proposedValue: Double?,
    val delta: Double?,
    val confidence: Double,
    val requiresConfirmation: Boolean,
    val clarification: String? = null,
    val persistence: String = "PERSISTENT"
) {
    fun toJson(): String {
        fun number(value: Double?): String = value?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "null"
        val targetJson = target?.key?.let { "\"$it\"" } ?: "null"
        val operationJson = operation?.name?.let { "\"$it\"" } ?: "null"
        val clarificationJson = clarification?.let { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" } ?: "null"
        return """{"intent":"$intent","target":$targetJson,"operation":$operationJson,"current_value":${number(currentValue)},"proposed_value":${number(proposedValue)},"delta":${number(delta)},"confidence":${number(confidence)},"requires_confirmation":$requiresConfirmation,"clarification":$clarificationJson,"persistence":"$persistence"}"""
    }
}

/** Deterministic first-pass parser. It never writes settings or calls a model. */
object NatashaIntentEngine {
    private const val STANDARD_STEP = 0.1
    private const val AUTO_SAVE_CONFIDENCE = 0.85

    private val aliases = mapOf(
        NatashaSettingDimension.ASSERTIVENESS to listOf("assertiveness", "assertive", "directness", "direct", "blunt", "reserved", "softer"),
        NatashaSettingDimension.AFFECTION to listOf("affection", "affectionate", "warmth", "warm", "nurturing", "distant"),
        NatashaSettingDimension.INITIATIVE to listOf("initiative", "proactive", "driving", "passive", "reactive"),
        NatashaSettingDimension.SEXUAL_EXPRESSIVENESS to listOf("sexual expressiveness", "sexually expressive", "sexual expression", "sexually forward", "provocative", "mild", "uncensored"),
        NatashaSettingDimension.PLAYFULNESS to listOf("playfulness", "playful", "humor", "humorous", "teasing", "flirty", "serious", "deadpan"),
        NatashaSettingDimension.DOMINANCE to listOf("dominance", "dominant", "commanding", "accommodating", "lead", "follow"),
        NatashaSettingDimension.EMOTIONAL_DEPTH to listOf("emotional depth", "emotionally deep", "depth", "substantive", "surface", "light"),
        NatashaSettingDimension.CONVERSATIONAL_VARIETY to listOf("conversational variety", "variety", "dynamic", "monotone", "rhythmic", "bursting", "less repetitive")
    )

    fun parse(inputText: String, currentValues: Map<NatashaSettingDimension, Double> = emptyMap()): NatashaIntentResult? {
        val text = inputText.trim().lowercase()
        if (text.isEmpty() || text.length > 600) return null

        val reset = Regex("^(?:reset|restore|clear) (?:my |your )?([a-z ]+?)(?: setting| preference)?$").matchEntire(text)
        if (reset != null) {
            val dimension = findDimension(reset.groupValues[1])
            if (dimension != null) {
                val current = currentValue(dimension, currentValues)
                return result(NatashaIntent.RESET_SETTING, dimension, NatashaSettingOperation.SET, current,
                    dimension.defaultValue, dimension.defaultValue - current, 0.96, false)
            }
        }

        val setMatch = Regex("^(?:set|make) (?:your |the )?(.+?) (?:to|at) ([0-9]+(?:\\.[0-9]+)?)$").matchEntire(text)
        if (setMatch != null) {
            val dimension = findDimension(setMatch.groupValues[1])
            val requested = setMatch.groupValues[2].toDoubleOrNull()
            if (dimension != null && requested != null) {
                val current = currentValue(dimension, currentValues)
                val bounded = requested.coerceIn(dimension.minimum, dimension.maximum)
                val outOfRange = requested != bounded
                return result(intentFor(dimension), dimension, NatashaSettingOperation.SET, current, bounded,
                    bounded - current, if (outOfRange) 0.72 else 0.97, outOfRange,
                    if (outOfRange) "That value is outside the ${dimension.key} range (${dimension.minimum}–${dimension.maximum}). Use the nearest allowed value?" else null)
            }
        }

        val command = Regex("^(?:please )?(?:make yourself |be |become |get )?(more|less|stronger|softer|increase|decrease|boost|reduce) (.+)$").matchEntire(text)
        if (command != null) {
            val modifier = command.groupValues[1]
            val phrase = command.groupValues[2].trim()
            val dimension = findDimension(phrase)
            if (dimension != null) {
                val current = currentValue(dimension, currentValues)
                val increasing = modifier in listOf("more", "stronger", "increase", "boost")
                val operation = if (increasing) NatashaSettingOperation.INCREASE else NatashaSettingOperation.DECREASE
                val proposed = (current + if (increasing) STANDARD_STEP else -STANDARD_STEP).coerceIn(dimension.minimum, dimension.maximum)
                return result(intentFor(dimension), dimension, operation, current, proposed, proposed - current, 0.96, false)
            }
            if (phrase in listOf("intense", "intensity", "nicer", "better", "more engaging")) {
                return NatashaIntentResult(NatashaIntent.ALTER_PERSONALITY, null, null, null, null, null,
                    0.55, true, "Which setting should I adjust—assertiveness, affection, initiative, playfulness, or another dimension?")
            }
        }

        val stopMatch = Regex("^(?:stop|turn off|disable) (.+)$").matchEntire(text)
        if (stopMatch != null) {
            val dimension = findDimension(stopMatch.groupValues[1])
            if (dimension != null) {
                val current = currentValue(dimension, currentValues)
                return result(intentFor(dimension), dimension, NatashaSettingOperation.STOP, current,
                    dimension.minimum, dimension.minimum - current, 0.91, false)
            }
        }
        return null
    }

    private fun findDimension(phrase: String): NatashaSettingDimension? {
        val normalized = phrase.trim().removePrefix("your ").removeSuffix(" setting")
        return aliases.entries.firstOrNull { (_, words) -> words.any { normalized == it || normalized.contains(it) } }?.key
    }

    private fun intentFor(dimension: NatashaSettingDimension): NatashaIntent = when (dimension) {
        NatashaSettingDimension.INITIATIVE -> NatashaIntent.SET_INITIATIVE
        NatashaSettingDimension.AFFECTION -> NatashaIntent.SET_AFFECTION
        else -> NatashaIntent.ALTER_PERSONALITY
    }

    private fun currentValue(dimension: NatashaSettingDimension, values: Map<NatashaSettingDimension, Double>): Double =
        (values[dimension] ?: dimension.defaultValue).coerceIn(dimension.minimum, dimension.maximum)

    private fun result(
        intent: NatashaIntent, target: NatashaSettingDimension, operation: NatashaSettingOperation,
        current: Double, proposed: Double, delta: Double, confidence: Double, requiresConfirmation: Boolean,
        clarification: String? = null
    ) = NatashaIntentResult(intent, target, operation, current, proposed, delta, confidence,
        requiresConfirmation || confidence < AUTO_SAVE_CONFIDENCE, clarification)
}
