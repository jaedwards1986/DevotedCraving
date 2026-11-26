package com.forgeai.chat.data.context

/**
 * Deterministic, model-independent classification of the user's current conversational intent.
 * This is deliberately conservative: intensity is applied only when the user clearly signals
 * roleplay or intimate roleplay, never merely because a message contains an isolated keyword.
 */
enum class InteractionIntent {
    UTILITY,
    FACTUAL,
    TECHNICAL,
    CREATIVE,
    ROLEPLAY,
    INTIMATE_ROLEPLAY,
    GENERAL
}

data class FidelityDecision(
    val intent: InteractionIntent,
    val configuredIntensity: Int,
    val appliedIntensity: Int,
    val directive: String
)

/**
 * Converts explicit user intent and the saved 0..5 setting into a per-turn directive.
 * The setting never overrides the current request and is not allowed to sexualize unrelated tasks.
 */
object IntentAwareFidelity {
    fun assess(userMessage: String, configuredIntensity: Int): FidelityDecision {
        val text = userMessage.trim().lowercase()
        val intensity = configuredIntensity.coerceIn(0, 5)
        val intent = classify(text)
        val applied = if (
            intent == InteractionIntent.ROLEPLAY ||
            intent == InteractionIntent.INTIMATE_ROLEPLAY
        ) intensity else 0

        val directive = when (intent) {
            InteractionIntent.UTILITY,
            InteractionIntent.FACTUAL,
            InteractionIntent.TECHNICAL ->
                "Prioritize the user's practical request. Keep Natasha's established voice where it helps, but do not introduce flirtation, sexual framing, or roleplay unless the user asks."

            InteractionIntent.CREATIVE ->
                "Fulfil the creative request in its requested genre and tone. Do not assume it is erotic roleplay."

            InteractionIntent.ROLEPLAY ->
                "Continue the user's requested fictional roleplay. Match its established tone and pace; do not escalate beyond the user's cues. Configured roleplay intensity: $applied of 5."

            InteractionIntent.INTIMATE_ROLEPLAY ->
                "The user has explicitly requested intimate adult fictional roleplay. Follow the established character, consent boundaries, and the configured intensity of $applied of 5. Do not exceed the user's latest direction."

            InteractionIntent.GENERAL ->
                "Respond to the user's actual message. Preserve Natasha's recognizable character voice without forcing flirtation or escalation."
        }

        return FidelityDecision(intent, intensity, applied, directive)
    }

    private fun classify(text: String): InteractionIntent {
        if (text.isBlank()) return InteractionIntent.GENERAL

        val technical = listOf(
            "code", "kotlin", "android", "debug", "build", "compile", "github",
            "error", "architecture", "api", "database", "test", "install", "apk"
        )
        val utility = listOf(
            "weather", "time", "temperature outside", "convert", "calculate",
            "directions", "remind me", "schedule", "how far"
        )
        val factual = listOf(
            "what is", "who is", "when did", "where is", "explain", "define",
            "compare", "summarize", "research", "latest"
        )
        if (technical.any(text::contains)) return InteractionIntent.TECHNICAL
        if (utility.any(text::contains)) return InteractionIntent.UTILITY
        if (factual.any(text::contains)) return InteractionIntent.FACTUAL

        val intimateSignals = listOf(
            "erotic roleplay", "sexual roleplay", "intimate roleplay",
            "explicit roleplay", "sex scene", "intimate scene"
        )
        if (intimateSignals.any(text::contains)) return InteractionIntent.INTIMATE_ROLEPLAY

        val roleplaySignals = listOf(
            "roleplay", "role-play", " rp ", "in character", "our scene",
            "continue the scene", "flirt with me", "tease me", "seduce me"
        )
        if (roleplaySignals.any(text::contains)) return InteractionIntent.ROLEPLAY

        val creativeSignals = listOf(
            "write a story", "write a poem", "draft a", "create a character",
            "make a scene", "fictional story", "brainstorm"
        )
        if (creativeSignals.any(text::contains)) return InteractionIntent.CREATIVE

        return InteractionIntent.GENERAL
    }
}
