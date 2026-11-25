package com.forgeai.chat.domain.settings

import com.forgeai.chat.data.preferences.PersonalitySnapshot

data class AssertivePresenceResult(
    val observedScore: Double,
    val rawObservedScore: Double,
    val targetScore: Double,
    val alignmentScore: Double,
    val hostilityPenalty: Double,
    val hostilityDetected: Boolean,
    val requiresReview: Boolean
)

class AssertivePresenceExtractor(
    private val detector: LexicalMarkerDetector = DefaultLexicalMarkerDetector(),
    private val contextualScorer: ContextualScorer = ContextualScorer(),
    private val tolerance: Double = 15.0
) {
    private val alignmentMatcher = AlignmentMatcher(tolerance)

    fun evaluate(text: String, snapshot: PersonalitySnapshot): AssertivePresenceResult {
        val evidence = detector.detect(text)
        val contextual = contextualScorer.score(text, evidence)
        val hostilityPenalty = evidence.hostilityPenalty

        // Sarcasm ambiguity is discounted by ContextualScorer and remains
        // explicit; it is never reinterpreted as hostility or opposite intent.
        val observed = (contextual.observedScore - hostilityPenalty * .15)
            .coerceIn(0.0, 100.0)
        val rawObserved = (contextual.rawObservedScore - hostilityPenalty * .15)
            .coerceIn(0.0, 100.0)

        val dimension = NatashaSettingDimension.ASSERTIVENESS
        val normalizedTarget = (
            (snapshot.value(dimension) - dimension.minimum) /
                (dimension.maximum - dimension.minimum)
            ).coerceIn(0.0, 1.0)
        val target = normalizedTarget * 100.0

        val hostilityDetected = evidence.matches.any {
            it.category == MarkerCategory.HOSTILITY &&
                !it.isQuoted && !it.isNegated && !it.isMetaDiscussion &&
                (it.hostilityLevel == HostilityLevel.AGGRESSIVE ||
                    it.hostilityLevel == HostilityLevel.THREATENING)
        }
        val alignment = alignmentMatcher.match(
            observedScore = observed,
            targetScore = target,
            hardGateTriggered = hostilityDetected
        ).score
        return AssertivePresenceResult(
            observedScore = observed,
            rawObservedScore = rawObserved,
            targetScore = target,
            alignmentScore = alignment,
            hostilityPenalty = hostilityPenalty,
            hostilityDetected = hostilityDetected,
            requiresReview = contextual.requiresReview
        )
    }
}
