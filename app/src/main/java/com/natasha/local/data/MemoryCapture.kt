package com.natasha.local.data

/**
 * Conservative deterministic capture: only explicit first-person preferences
 * and facts are stored. It never asks the model to invent a user profile.
 */
object MemoryCapture {
    private val cues = listOf(
        Regex("(?i)\\bremember that\\s+([^.!?\\n]{4,220})"),
        Regex("(?i)\\bI (?:prefer|like|love|hate|dislike|always use|usually use)\\s+([^.!?\\n]{4,220})"),
        Regex("(?i)\\bmy (?:app|project|phone|device) (?:is called|is named|is)\\s+([^.!?\\n]{4,180})"),
        Regex("(?i)\\bI(?:'m| am) building\\s+([^.!?\\n]{4,180})")
    )
    private val sensitive = Regex(
        "(?i)\\b(password|passcode|social security|ssn|credit card|bank account|" +
            "medical|diagnosis|medication|religion|political party|sexual orientation|" +
            "sex life|criminal record|immigration status)\\b"
    )

    fun extract(message: String): List<String> {
        if (message.length > 4000 || sensitive.containsMatchIn(message)) return emptyList()
        return cues.mapNotNull { pattern ->
            pattern.find(message)?.let {
                val sentence = message.substringBefore(Regex("[.!?\\n]")).trim()
                sentence.takeIf { value -> value.length in 5..240 }
            }
        }.distinctBy { it.lowercase() }.take(3)
    }
}