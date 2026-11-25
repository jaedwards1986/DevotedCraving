package com.forgeai.chat.domain.settings

import com.forgeai.chat.data.preferences.PersonalitySnapshot
import kotlin.math.abs

data class AssertivePresenceResult(
    val observedScore: Double,
    val targetScore: Double,
    val alignmentScore: Double,
    val hostilityPenalty: Double,
    val hostilityDetected: Boolean,
    val requiresReview: Boolean
)

class AssertivePresenceExtractor(
    private val detector: LexicalMarkerDetector = DefaultLexicalMarkerDetector(),
    private val tolerance: Double = 15.0
) {
    init {
        require(tolerance > 0.0 && tolerance <= 100.0)
    }

    fun evaluate(text: String, snapshot: PersonalitySnapshot): AssertivePresenceResult {
        val evidence = detector.detect(text)
        val signals = evidence.positiveEvidence
        val declarative = signals[MarkerCategory.DECLARATIVE_CONFIDENCE] ?: 0.0
        val decisive = signals[MarkerCategory.DECISIVE_LANGUAGE] ?: 0.0
        val leadership = signals[MarkerCategory.CONVERSATIONAL_LEADERSHIP] ?: 0.0
        val respectful = signals[MarkerCategory.RESPECTFUL_DIRECTNESS] ?: 0.0

        // Evidence is confidence-weighted and category-capped; repeated phrases
        // cannot inflate a dimension indefinitely.
        val positive = (
            .25 * declarative +
                .25 * decisive +
                .20 * leadership +
                .15 * respectful
            ) * 100.0
        val hostilityPenalty = evidence.hostilityPenalty
        val observed = (positive - hostilityPenalty * .15).coerceIn(0.0, 100.0)
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
        val rawDifference = abs(observed - target)
        val alignment = if (hostilityDetected) {
            0.0
        } else {
            (100.0 - (rawDifference / tolerance * 25.0)).coerceIn(0.0, 100.0)
        }
        return AssertivePresenceResult(
            observedScore = observed,
            targetScore = target,
            alignmentScore = alignment,
            hostilityPenalty = hostilityPenalty,
            hostilityDetected = hostilityDetected,
            requiresReview = evidence.requiresReview
        )
    }
}
