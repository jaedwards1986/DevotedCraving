package com.forgeai.chat.domain.settings

/**
 * Converts lexical matches into a bounded, confidence-weighted signal.
 *
 * SSF(category) = 1 - product(1 - confidence(match) * strength(category)).
 * Repeated identical matches in one sentence are deduplicated. Distinct evidence
 * accumulates multiplicatively but naturally saturates below 1.0.
 *
 * Sarcasm is uncertainty, not negative evidence: the raw score is retained for
 * diagnostics while the score sent to alignment is discounted and flagged.
 */
data class ContextualScoreResult(
    val observedScore: Double,
    val rawObservedScore: Double,
    val evidenceStrength: Double,
    val requiresReview: Boolean,
    val acceptedEvidence: Map<MarkerCategory, Double>
)

class ContextualScorer(
    private val ambiguityMultiplier: Double = 0.5,
    private val categoryStrength: Map<MarkerCategory, Double> = DEFAULT_CATEGORY_STRENGTH,
    private val categoryWeights: Map<MarkerCategory, Double> = DEFAULT_CATEGORY_WEIGHTS
) {
    init {
        require(ambiguityMultiplier.isFinite() && ambiguityMultiplier in 0.0..1.0)
        require(categoryStrength.values.all { it.isFinite() && it in 0.0..1.0 })
        require(categoryWeights.values.all { it.isFinite() && it >= 0.0 })
        require(categoryWeights.values.sum() <= 1.0 + 1e-9)
    }

    fun score(text: String, detection: LexicalDetectionResult): ContextualScoreResult {
        val eligible = detection.matches.filter {
            it.category != MarkerCategory.HOSTILITY &&
                !it.isQuoted && !it.isNegated && !it.isMetaDiscussion &&
                it.confidence.isFinite() && it.confidence in 0.0..1.0
        }
        val unique = eligible.distinctBy { match ->
            Triple(match.category, match.phrase.lowercase(), sentenceIndex(text, match.startIndex))
        }

        val categoryScores = MarkerCategory.entries
            .filter { it != MarkerCategory.HOSTILITY }
            .associateWith { category ->
                val strength = categoryStrength[category] ?: 0.0
                val matches = unique.filter { it.category == category }
                1.0 - matches.fold(1.0) { remaining, match ->
                    remaining * (1.0 - match.confidence * strength)
                }
            }

        val raw = (categoryScores.entries.sumOf { (category, score) ->
            score * (categoryWeights[category] ?: 0.0)
        } * 100.0).coerceIn(0.0, 100.0)

        val ambiguous = detection.requiresReview
        val observed = if (ambiguous) raw * ambiguityMultiplier else raw
        val strength = if (unique.isEmpty()) 0.0 else {
            unique.sumOf { it.confidence * (categoryStrength[it.category] ?: 0.0) } /
                unique.size.toDouble()
        }

        return ContextualScoreResult(
            observedScore = observed,
            rawObservedScore = raw,
            evidenceStrength = strength.coerceIn(0.0, 1.0),
            requiresReview = ambiguous,
            acceptedEvidence = categoryScores
        )
    }

    private fun sentenceIndex(text: String, offset: Int): Int {
        if (offset <= 0 || text.isEmpty()) return 0
        return text.take(offset.coerceAtMost(text.length))
            .count { it == '.' || it == '!' || it == '?' || it == '\n' }
    }

    companion object {
        val DEFAULT_CATEGORY_STRENGTH = mapOf(
            MarkerCategory.DECLARATIVE_CONFIDENCE to 1.0,
            MarkerCategory.DECISIVE_LANGUAGE to 1.0,
            MarkerCategory.CONVERSATIONAL_LEADERSHIP to 0.9,
            MarkerCategory.RESPECTFUL_DIRECTNESS to 0.8
        )

        // Sum = 0.85; remaining headroom is intentional rather than silently
        // renormalizing the metric when categories are absent.
        val DEFAULT_CATEGORY_WEIGHTS = mapOf(
            MarkerCategory.DECLARATIVE_CONFIDENCE to 0.25,
            MarkerCategory.DECISIVE_LANGUAGE to 0.25,
            MarkerCategory.CONVERSATIONAL_LEADERSHIP to 0.20,
            MarkerCategory.RESPECTFUL_DIRECTNESS to 0.15
        )
    }
}
