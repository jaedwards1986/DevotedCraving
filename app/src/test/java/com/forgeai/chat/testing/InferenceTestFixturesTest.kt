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
