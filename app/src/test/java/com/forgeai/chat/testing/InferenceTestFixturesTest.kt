package com.forgeai.chat.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceTestFixturesTest {
    @Test fun createsRequestedFixtureCountWithStableIds() {
        val fixtures = InferenceTestFixtures.create(100)
        assertEquals(100, fixtures.size)
        assertEquals("fixture-001", fixtures.first().id)
        assertEquals("fixture-100", fixtures.last().id)
    }

    @Test fun samplerBoundaryValuesAreExplicitlyValidated() {
        assertTrue(SamplerConfig(temperature = 1.5f).validate().isEmpty())
        assertTrue(SamplerConfig(temperature = 0.01f).validate().isEmpty())
        assertFalse(SamplerConfig(temperature = Float.NaN).validate().isEmpty())
        assertFalse(SamplerConfig(topP = 1.01f).validate().isEmpty())
        assertFalse(SamplerConfig(frequencyPenalty = Float.POSITIVE_INFINITY).validate().isEmpty())
    }

    @Test fun isolationMatrixChangesOnlyOneVariablePerGroup() {
        val base = SamplerConfig()
        val matrix = InferenceTestFixtures.isolationMatrix(base)
        assertEquals(setOf("temperature", "topP", "frequencyPenalty", "presencePenalty"), matrix.keys)
        matrix.getValue("temperature").forEach {
            assertEquals(base.topP, it.topP)
            assertEquals(base.frequencyPenalty, it.frequencyPenalty)
            assertEquals(base.presencePenalty, it.presencePenalty)
        }
    }

    @Test fun personaSuiteContainsAdultBoundaryAndContinuityCases() {
        val suite = InferenceTestFixtures.personaSuite()
        assertTrue(suite.size >= 8)
        assertTrue(suite.all { it.category == "persona" })
        assertTrue(suite.any { it.prompt.contains("age unclear") })
        assertTrue(suite.any { it.prompt.contains("sets a boundary") })
    }

    @Test fun highVarianceSamplerMatrixStaysWithinValidBounds() {
        val matrix = InferenceTestFixtures.highVarianceMatrix()
        assertTrue(matrix.size >= 4)
        assertTrue(matrix.all { it.temperature >= 1.2f && it.topP >= 0.95f })
        assertTrue(matrix.all { it.validate().isEmpty() })
    }

    @Test fun personaFidelityUsesProductOfAverageRatings() {
        val score = PersonaFidelityScorer.summarize(
            listOf(PersonaRating(5, 4), PersonaRating(3, 2))
        )
        assertEquals(4.0, score.averageIntensity, 0.0001)
        assertEquals(3.0, score.averageExpressiveDirectness, 0.0001)
        assertEquals(12.0, score.fidelityScore, 0.0001)
        assertEquals(2, score.sampleCount)
    }

    @Test fun contextFixturesDefineIncreasingTargetFractions() {
        val fractions = InferenceTestFixtures.contextStressFixtures().map { it.contextLoadFraction }
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 0.9f), fractions)
    }

    @Test fun traceRequiresOrderedTransitions() {
        var now = 100L
        val trace = InferenceTransactionTrace { now++ }
        trace.mark(InferenceStage.REQUEST_SENT)
        trace.mark(InferenceStage.NATIVE_CALL_INITIATED)
        assertEquals(2, trace.snapshot().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun traceRejectsSkippedTransitions() {
        InferenceTransactionTrace().mark(InferenceStage.TOKEN_STREAM_RECEIVED)
    }
}
