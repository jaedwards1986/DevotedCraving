package com.forgeai.chat.domain.settings

import org.junit.Assert.*
import org.junit.Test

class HPVEvaluatorTest {
    private val evaluator = HPVEvaluator()

    private fun complete(score: Double, review: Boolean = false) =
        HpvDimension.entries.associateWith {
            HpvComponentScore(score = score, requiresReview = review)
        }

    @Test fun ordinaryWeightsProduceWeightedAggregate() {
        val scores = mapOf(
            HpvDimension.WARMTH to HpvComponentScore(80.0),
            HpvDimension.PRESENCE to HpvComponentScore(70.0),
            HpvDimension.ASSERTIVENESS to HpvComponentScore(60.0),
            HpvDimension.SEXUAL_CONFIDENCE to HpvComponentScore(50.0),
            HpvDimension.INDIVIDUALITY to HpvComponentScore(90.0)
        )
        val result = evaluator.evaluate(HpvEvaluationMode.ORDINARY, scores)
        assertTrue(result.isComplete)
        assertEquals(1.0, result.coverage, 0.000001)
        assertEquals(71.5, result.overallScore!!, 0.000001)
        assertEquals(result.overallScore, result.partialScore)
        assertTrue(result.missingDimensions.isEmpty())
    }

    @Test fun intimateModeUsesItsOwnWeightProfile() {
        val scores = mapOf(
            HpvDimension.WARMTH to HpvComponentScore(0.0),
            HpvDimension.PRESENCE to HpvComponentScore(0.0),
            HpvDimension.ASSERTIVENESS to HpvComponentScore(0.0),
            HpvDimension.SEXUAL_CONFIDENCE to HpvComponentScore(100.0),
            HpvDimension.INDIVIDUALITY to HpvComponentScore(0.0)
        )
        val ordinary = evaluator.evaluate(HpvEvaluationMode.ORDINARY, scores)
        val intimate = evaluator.evaluate(HpvEvaluationMode.INTIMATE, scores)
        assertEquals(15.0, ordinary.overallScore!!, 0.000001)
        assertEquals(30.0, intimate.overallScore!!, 0.000001)
    }

    @Test fun sexualConfidenceExtractorFeedsOnlyItsOwnHpvDimension() {
        val measured = SexualConfidenceExtractor().evaluate(
            "I have an idea. I want to be clear and lead this conversation.",
            "How was your day?"
        )
        val result = evaluator.evaluateSexualConfidence(HpvEvaluationMode.INTIMATE, measured)
        assertEquals(setOf(HpvDimension.SEXUAL_CONFIDENCE), result.componentScores.keys)
        assertEquals(0.30, result.coverage, 0.000001)
        assertNull(result.overallScore)
        assertNotNull(result.partialScore)
        assertEquals(measured.score, result.componentScores.getValue(HpvDimension.SEXUAL_CONFIDENCE).score, 0.0)
    }

    @Test fun missingDimensionsNeverBecomePerfectScores() {
        val result = evaluator.evaluate(
            HpvEvaluationMode.ORDINARY,
            mapOf(HpvDimension.ASSERTIVENESS to HpvComponentScore(80.0))
        )
        assertFalse(result.isComplete)
        assertNull(result.overallScore)
        assertNotNull(result.partialScore)
        assertEquals(0.20, result.coverage, 0.000001)
        assertEquals(4, result.missingDimensions.size)
    }

    @Test fun partialScoreIsNormalizedOverAvailableWeightForDiagnosticsOnly() {
        val result = evaluator.evaluate(
            HpvEvaluationMode.ORDINARY,
            mapOf(
                HpvDimension.WARMTH to HpvComponentScore(60.0),
                HpvDimension.ASSERTIVENESS to HpvComponentScore(80.0)
            )
        )
        assertEquals((60.0 * .25 + 80.0 * .20) / .45, result.partialScore!!, 0.000001)
        assertNull(result.overallScore)
    }

    @Test fun reviewStatePropagatesWithoutChangingNumericalAggregate() {
        val result = evaluator.evaluate(
            HpvEvaluationMode.ORDINARY,
            complete(75.0, review = true)
        )
        assertTrue(result.requiresReview)
        assertEquals(75.0, result.overallScore!!, 0.000001)
    }

    @Test fun emptyInputHasNoScoreAndZeroCoverage() {
        val result = evaluator.evaluate(HpvEvaluationMode.ORDINARY, emptyMap())
        assertNull(result.overallScore)
        assertNull(result.partialScore)
        assertEquals(0.0, result.coverage, 0.0)
        assertFalse(result.isComplete)
    }

    @Test(expected = IllegalArgumentException::class)
    fun componentScoresMustBeFiniteAndBounded() {
        HpvComponentScore(Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun customWeightsMustSumToOne() {
        val invalid = HPVEvaluator.DEFAULT_WEIGHTS.toMutableMap()
        invalid[HpvEvaluationMode.ORDINARY] =
            HPVEvaluator.DEFAULT_WEIGHTS.getValue(HpvEvaluationMode.ORDINARY)
                .toMutableMap().apply { this[HpvDimension.WARMTH] = 0.5 }
        HPVEvaluator(invalid)
    }
}
