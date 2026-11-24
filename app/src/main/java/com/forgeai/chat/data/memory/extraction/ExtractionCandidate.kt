package com.forgeai.chat.data.memory.extraction

/**
 * A deterministic extraction result. This is a proposal only: it must pass through
 * consent and conflict-resolution layers before anything is written to persistence.
 */
data class ExtractionCandidate(
    val category: ExtractionCategory,
    val value: String?,
    val confidence: Double,
    val sensitivity: SensitivityLevel,
    val sourceMessageId: Long? = null,
    val matchedRule: String,
    val previousValue: String? = null
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be between 0 and 1." }
        require(matchedRule.isNotBlank()) { "A candidate must identify its matching rule." }
        require(category == ExtractionCategory.SENSITIVE_DATA || !value.isNullOrBlank()) {
            "Only sensitive-data signals may omit a value."
        }
    }
}

enum class ExtractionCategory {
    PREFERENCE,
    DISLIKE,
    EVENT,
    CORRECTION,
    UNCERTAIN,
    SENSITIVE_DATA
}

enum class SensitivityLevel {
    LOW,
    MEDIUM,
    HIGH
}
