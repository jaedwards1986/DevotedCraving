package com.forgeai.chat.domain.settings

data class SettingsCommandResult(
    val updatedSystemPrompt: String,
    val confirmation: String
)

/**
 * Applies direct, explicit chat commands to Natasha's editable preferences.
 * Ordinary messages are left alone. A setting is replaced by category so
 * repeated adjustments do not create a contradictory pile of instructions.
 */
object NatashaSettingsCommandParser {
    private const val MAX_INSTRUCTION_LENGTH = 600
    private const val PREFERENCES_HEADER = "USER-EDITABLE PREFERENCES"

    fun apply(message: String, currentSystemPrompt: String): SettingsCommandResult? {
        val text = message.trim()
        if (text.isEmpty()) return null

        val name = Regex("(?i)^(?:call me|address me as)\\s+(.+)$").matchEntire(text)
            ?.groupValues?.get(1)?.trim()?.takeIf(::validValue)
        if (name != null) {
            return replacePreference(
                currentSystemPrompt,
                "Addressing preference:",
                "Addressing preference: Address the user as \"$name\".",
                "Got it. I'll call you $name."
            )
        }

        val style = Regex(
            "(?i)^(?:make yourself more|be more|change your personality to|change your style to|from now on be)\\s+(.+)$"
        ).matchEntire(text)?.groupValues?.get(1)?.trim()?.takeIf(::validValue)
        if (style != null) {
            return replacePreference(
                currentSystemPrompt,
                "User-requested style preference:",
                "User-requested style preference: $style. Apply this consistently while adapting naturally to the conversation.",
                "Understood. I've updated my style preference to: $style."
            )
        }

        val length = Regex(
            "(?i)^(?:make your replies|make your responses)\\s+(longer|shorter|more detailed|more concise)$"
        ).matchEntire(text)?.groupValues?.get(1)?.lowercase()
        if (length != null) {
            val instruction = when (length) {
                "longer", "more detailed" -> "Prefer fuller, more detailed replies when useful."
                else -> "Prefer shorter, more concise replies and avoid unnecessary elaboration."
            }
            return replacePreference(
                currentSystemPrompt,
                "Response-length preference:",
                "Response-length preference: $instruction",
                "Reply length preference updated."
            )
        }

        val reset = Regex("(?i)^(?:reset|clear|remove) my (?:custom )?style preference$").matches(text)
        if (reset) {
            return removePreference(
                currentSystemPrompt,
                "User-requested style preference:",
                "I've cleared that custom style setting."
            )
        }
        return null
    }

    private fun replacePreference(
        current: String,
        key: String,
        instruction: String,
        confirmation: String
    ): SettingsCommandResult {
        val lines = current.trim().lines().toMutableList()
        val headerIndex = lines.indexOfFirst { it.trim().equals(PREFERENCES_HEADER, ignoreCase = true) }
        if (headerIndex >= 0) {
            val prefix = lines.take(headerIndex).joinToString("\n").trimEnd()
            val existing = lines.drop(headerIndex + 1)
                .filterNot { it.trimStart().startsWith("- $key", ignoreCase = true) }
                .filter { it.isNotBlank() }
            val updatedLines = (existing + "- $instruction").distinct()
            val updated = listOf(prefix, PREFERENCES_HEADER, updatedLines.joinToString("\n"))
                .filter { it.isNotBlank() }.joinToString("\n\n")
            return SettingsCommandResult(updated, confirmation)
        }
        val updated = listOf(current.trim(), PREFERENCES_HEADER, "- $instruction")
            .filter { it.isNotBlank() }.joinToString("\n\n")
        return SettingsCommandResult(updated, confirmation)
    }

    private fun removePreference(current: String, key: String, confirmation: String): SettingsCommandResult {
        val lines = current.trim().lines()
        val headerIndex = lines.indexOfFirst { it.trim().equals(PREFERENCES_HEADER, ignoreCase = true) }
        if (headerIndex < 0) return SettingsCommandResult(current.trim(), confirmation)
        val prefix = lines.take(headerIndex).joinToString("\n").trimEnd()
        val remaining = lines.drop(headerIndex + 1)
            .filterNot { it.trimStart().startsWith("- $key", ignoreCase = true) }
            .filter { it.isNotBlank() }
        val updated = listOf(prefix, if (remaining.isEmpty()) "" else PREFERENCES_HEADER, remaining.joinToString("\n"))
            .filter { it.isNotBlank() }.joinToString("\n\n")
        return SettingsCommandResult(updated, confirmation)
    }

    private fun validValue(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_INSTRUCTION_LENGTH &&
            value.none { it == '\n' || it == '\r' }
}
