package com.forgeai.chat.data.memory.extraction

/**
 * Small deterministic v0.1 extractor. It classifies only explicit first-person
 * statements and emits candidates; it never persists, approves, or overwrites memory.
 */
class RuleBasedTextExtractor : TextExtractor {

    override fun extract(text: String, sourceMessageId: Long?): List<ExtractionCandidate> {
        val input = text.trim()
        if (input.isEmpty()) return emptyList()

        // Sensitive-data detection takes precedence. Do not copy the secret into a candidate.
        if (ExtractionRules.sensitiveData.any { it.containsMatchIn(input) }) {
            return listOf(
                ExtractionCandidate(
                    category = ExtractionCategory.SENSITIVE_DATA,
                    value = null,
                    confidence = 0.99,
                    sensitivity = SensitivityLevel.HIGH,
                    sourceMessageId = sourceMessageId,
                    matchedRule = "sensitive-data-signal"
                )
            )
        }

        ExtractionRules.uncertain.matchEntire(input)?.let { match ->
            return candidate(
                category = ExtractionCategory.UNCERTAIN,
                value = match.groupValues[1].trim(),
                confidence = 0.90,
                sourceMessageId = sourceMessageId,
                rule = "uncertain-first-person"
            )
        }

        ExtractionRules.negativePreference.matchEntire(input)?.let { match ->
            return candidate(
                category = ExtractionCategory.DISLIKE,
                value = match.groupValues[1].trim(),
                confidence = 0.96,
                sourceMessageId = sourceMessageId,
                rule = "explicit-negative-preference"
            )
        }

        ExtractionRules.positivePreference.matchEntire(input)?.let { match ->
            return candidate(
                category = ExtractionCategory.PREFERENCE,
                value = match.groupValues[1].trim(),
                confidence = 0.96,
                sourceMessageId = sourceMessageId,
                rule = "explicit-positive-preference"
            )
        }

        ExtractionRules.decisionEvent.matchEntire(input)?.let { match ->
            return candidate(
                category = ExtractionCategory.EVENT,
                value = match.groupValues[1].trim(),
                confidence = 0.92,
                sourceMessageId = sourceMessageId,
                rule = "explicit-decision-event"
            )
        }

        ExtractionRules.correction.matchEntire(input)?.let { match ->
            return candidate(
                category = ExtractionCategory.CORRECTION,
                value = match.groupValues[1].trim(),
                confidence = 0.94,
                sourceMessageId = sourceMessageId,
                rule = "explicit-correction"
            )
        }

        ExtractionRules.notInsteadCorrection.matchEntire(input)?.let { match ->
            val corrected = match.groupValues[2].trim()
            val previous = match.groupValues[3].trim()
            return ExtractionCandidate(
                category = ExtractionCategory.CORRECTION,
                value = corrected,
                previousValue = previous,
                confidence = 0.93,
                sensitivity = SensitivityLevel.LOW,
                sourceMessageId = sourceMessageId,
                matchedRule = "explicit-not-instead-correction"
            )
        }

        return emptyList()
    }

    private fun candidate(
        category: ExtractionCategory,
        value: String,
        confidence: Double,
        sourceMessageId: Long?,
        rule: String
    ) = ExtractionCandidate(
        category = category,
        value = value,
        confidence = confidence,
        sensitivity = SensitivityLevel.LOW,
        sourceMessageId = sourceMessageId,
        matchedRule = rule
    )
}
