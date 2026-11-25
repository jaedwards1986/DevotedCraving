package com.forgeai.chat.domain.settings

/**
 * Detects explicit or strongly conventional sarcasm cues before lexical scoring.
 * A positive result means intent is ambiguous and should be reviewed; it is not
 * itself evidence of hostility or assertiveness.
 */
class SarcasmPreFilter {
    private val cues = listOf(
        Regex("""(?i)(?<![\\p{L}\\p{N}])/s(?![\\p{L}\\p{N}])"""),
        Regex("""(?i)\\byeah\\s*,?\\s*right\\b"""),
        Regex("""(?i)\\bas\\s+if\\b"""),
        Regex("""(?i)\\bwhat\\s+could\\s+possibly\\s+go\\s+wrong\\b"""),
        Regex("""(?i)\\b(oh\\s*,?\\s*)?(brilliant|genius|great)\\s+(idea|work|job)\\b""")
    )

    fun requiresReview(text: String): Boolean =
        text.isNotBlank() && cues.any { it.containsMatchIn(text) }
}
