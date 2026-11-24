package com.forgeai.chat.domain.settings

data class SettingsCommandResult(
    val updatedSystemPrompt: String,
    val confirmation: String
)

/** Handles explicit, predictable preference changes without intercepting ordinary chat. */
object NatashaSettingsCommandParser {
    private const val MAX_INSTRUCTION_LENGTH = 600

    fun apply(message: String, currentSystemPrompt: String): SettingsCommandResult? {
        val text = message.trim()
        if (text.isEmpty()) return null

        val name = Regex("(?i)^call me\\s+(.+)$").matchEntire(text)
            ?.groupValues?.get(1)?.trim()?.takeIf(::validValue)
        if (name != null) {
            return append(
                currentSystemPrompt,
                "Addressing preference: Address the user as \"$name\".",
                "I'll address you as $name."
            )
        }

        val style = Regex(
            "(?i)^(?:make yourself more|be more|change your personality to|change your style to)\\s+(.+)$"
        ).matchEntire(text)?.groupValues?.get(1)?.trim()?.takeIf(::validValue)
        if (style != null) {
            return append(
                currentSystemPrompt,
                "User-requested style preference: $style. Apply this preference naturally when relevant.",
                "Personality preference updated: $style."
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
            return append(currentSystemPrompt, "Response-length preference: $instruction", "Reply length preference updated.")
        }
        return null
    }

    private fun append(current: String, instruction: String, confirmation: String): SettingsCommandResult {
        val cleanCurrent = current.trim()
        val updated = if (cleanCurrent.contains(instruction, ignoreCase = true)) cleanCurrent
        else listOf(cleanCurrent, "USER-EDITABLE PREFERENCES", "- $instruction")
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        return SettingsCommandResult(updated, confirmation)
    }

    private fun validValue(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_INSTRUCTION_LENGTH &&
            value.none { it == '\n' || it == '\r' }
}
