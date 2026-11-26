package com.forgeai.chat.domain.settings

import kotlin.math.abs

data class AlignmentResult(
    val score: Double,
    val observedScore: Double,
    val targetScore: Double,
    val absoluteDifference: Double,
    val withinTolerance: Boolean,
    val hardGateTriggered: Boolean
)

/**
 * Compares measured behavior with a target derived from persisted personality state.
 * Scores are deterministic; hard-gate conditions (for example, active hostility)
 * override numerical proximity and force a zero alignment score.
 */
class AlignmentMatcher(
    private val tolerance: Double = 15.0
) {
    init {
        require(tolerance.isFinite() && tolerance > 0.0 && tolerance <= 100.0)
    }

    fun match(
        observedScore: Double,
        targetScore: Double,
        hardGateTriggered: Boolean = false
    ): AlignmentResult {
        require(observedScore.isFinite() && observedScore in 0.0..100.0)
        require(targetScore.isFinite() && targetScore in 0.0..100.0)

        val difference = abs(observedScore - targetScore)
        val withinTolerance = difference <= tolerance
        val score = when {
            hardGateTriggered -> 0.0
            withinTolerance -> 100.0
            else -> (100.0 - ((difference - tolerance) / (3.0 * tolerance) * 100.0))
                .coerceIn(0.0, 100.0)
        }
        return AlignmentResult(
            score = score,
            observedScore = observedScore,
            targetScore = targetScore,
            absoluteDifference = difference,
            withinTolerance = withinTolerance,
            hardGateTriggered = hardGateTriggered
        )
    }
}
