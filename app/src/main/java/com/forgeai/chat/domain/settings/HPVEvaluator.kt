package com.forgeai.chat.domain.settings

/**
 * Which response context is being evaluated. Ordinary and intimate responses
 * intentionally use different, fixed v1 weights.
 */
enum class HpvEvaluationMode { ORDINARY, INTIMATE }

enum class HpvDimension { WARMTH, PRESENCE, ASSERTIVENESS, SEXUAL_CONFIDENCE, INDIVIDUALITY }

data class HpvComponentScore(
    val score: Double,
    val requiresReview: Boolean = false,
    val evidenceReference: String? = null
) {
    init {
        require(score.isFinite() && score in 0.0..100.0) {
            "Component score must be finite and between 0 and 100."
        }
    }
}

data class HpvEvaluationResult(
    val mode: HpvEvaluationMode,
    /** Null until every required dimension has a measured score. */
    val overallScore: Double?,
    /** Weighted score over supplied dimensions only; never presented as complete. */
    val partialScore: Double?,
    val coverage: Double,
    val componentScores: Map<HpvDimension, HpvComponentScore>,
    val missingDimensions: Set<HpvDimension>,
    val requiresReview: Boolean,
    val isComplete: Boolean
)

/**
 * Aggregates measured HPV qualities. This class is deliberately pure:
 * it does not modify personality settings, reward history, or relationship memory.
 *
 * A missing component is not a perfect score and is not silently imputed.
 * The partial score is diagnostic only; overallScore remains null until complete.
 */
class HPVEvaluator(
    private val weightsByMode: Map<HpvEvaluationMode, Map<HpvDimension, Double>> = DEFAULT_WEIGHTS
) {
    init {
        require(weightsByMode.keys.containsAll(HpvEvaluationMode.entries))
        HpvEvaluationMode.entries.forEach { mode ->
            val weights = requireNotNull(weightsByMode[mode])
            require(weights.keys == HpvDimension.entries.toSet()) {
                "Each mode must define exactly one weight for every HPV dimension."
            }
            require(weights.values.all { it.isFinite() && it >= 0.0 })
            require(kotlin.math.abs(weights.values.sum() - 1.0) <= 1e-9) {
                "HPV weights for $mode must sum to 1.0."
            }
        }
    }

    fun evaluate(
        mode: HpvEvaluationMode,
        components: Map<HpvDimension, HpvComponentScore>
    ): HpvEvaluationResult {
        require(components.keys.all { it in HpvDimension.entries }) {
            "Unknown HPV component supplied."
        }

        val weights = requireNotNull(weightsByMode[mode])
        val missing = HpvDimension.entries.toSet() - components.keys
        val suppliedWeight = components.keys.sumOf { weights.getValue(it) }
        val partial = if (suppliedWeight > 0.0) {
            components.entries.sumOf { (dimension, component) ->
                component.score * weights.getValue(dimension)
            } / suppliedWeight
        } else {
            null
        }
        val complete = missing.isEmpty()
        val overall = if (complete) partial else null

        return HpvEvaluationResult(
            mode = mode,
            overallScore = overall,
            partialScore = partial,
            coverage = suppliedWeight.coerceIn(0.0, 1.0),
            componentScores = components.toMap(),
            missingDimensions = missing,
            requiresReview = components.values.any { it.requiresReview },
            isComplete = complete
        )
    }

    /** Adapts the currently implemented assertiveness extractor into the aggregate. */
    fun evaluateAssertiveness(
        mode: HpvEvaluationMode,
        assertiveness: AssertivePresenceResult
    ): HpvEvaluationResult = evaluate(
        mode,
        mapOf(
            HpvDimension.ASSERTIVENESS to HpvComponentScore(
                score = assertiveness.alignmentScore,
                requiresReview = assertiveness.requiresReview ||
                    assertiveness.hostilityDetected,
                evidenceReference = "assertiveness"
            )
        )
    )

    companion object {
        val DEFAULT_WEIGHTS: Map<HpvEvaluationMode, Map<HpvDimension, Double>> = mapOf(
            HpvEvaluationMode.ORDINARY to mapOf(
                HpvDimension.WARMTH to 0.25,
                HpvDimension.PRESENCE to 0.20,
                HpvDimension.ASSERTIVENESS to 0.20,
                HpvDimension.SEXUAL_CONFIDENCE to 0.15,
                HpvDimension.INDIVIDUALITY to 0.20
            ),
            HpvEvaluationMode.INTIMATE to mapOf(
                HpvDimension.WARMTH to 0.20,
                HpvDimension.PRESENCE to 0.15,
                HpvDimension.ASSERTIVENESS to 0.15,
                HpvDimension.SEXUAL_CONFIDENCE to 0.30,
                HpvDimension.INDIVIDUALITY to 0.20
            )
        )
    }
}
