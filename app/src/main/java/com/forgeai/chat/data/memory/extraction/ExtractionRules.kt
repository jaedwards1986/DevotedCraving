package com.forgeai.chat.data.memory.extraction

/**
 * v0.1 explicit-language rules. Patterns are deliberately narrow to favor precision
 * over recall; ambiguous statements are not promoted to durable facts.
 */
internal object ExtractionRules {
    val sensitiveData = listOf(
        Regex("""(?i)\b(password|passcode|one[- ]time code|security answer)\b"""),
        Regex("""\b\d{3}-\d{2}-\d{4}\b"""), // US SSN-shaped value
        Regex("""(?i)\b(?:credit|debit) card\b.*\b(?:\d[ -]*?){13,19}\b""")
    )

    val uncertain = Regex(
        """(?i)^\s*(?:maybe|perhaps|possibly|I might|I may|I'm considering|I am considering)\b(.+?)\s*[.!?]*$"""
    )

    val decisionEvent = Regex(
        """(?i)^\s*(?:we|I)\s+(?:have\s+)?(?:decided|chose|selected)\s+(.+?)\s*[.!?]*$"""
    )

    val correction = Regex(
        """(?i)^\s*(?:correction\s*:\s*|actually,?\s+|I meant\s+)(.+?)\s*[.!?]*$"""
    )

    val notInsteadCorrection = Regex(
        """(?i)^\s*(?:the\s+)?(.+?)\s+is\s+(.+?),\s+not\s+(.+?)\s*[.!?]*$"""
    )
}
