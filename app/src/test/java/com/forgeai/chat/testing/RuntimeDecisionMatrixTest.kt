package com.forgeai.chat.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDecisionMatrixTest {
    @Test
    fun fidelityHasHighestWeightAndCpuWinsFivePercentTie() {
        val observations = listOf(
            RuntimeObservation(RuntimeBackend.CPU, "HV-1", 9.0, 20.0, 1000, 100, true),
            RuntimeObservation(RuntimeBackend.VULKAN, "HV-1", 10.2, 20.0, 980, 100, true)
        )
        val complexity = listOf(
            BackendComplexity(RuntimeBackend.CPU, 1.0, 0.0),
            BackendComplexity(RuntimeBackend.VULKAN, 2.0, 0.0)
        )
        val result = RuntimeDecisionScorer.score(observations, complexity)
        assertEquals(RuntimeBackend.CPU, result.selected)
        assertTrue(result.reason.contains("5%"))
    }

    @Test
    fun fidelityCanOutweighSpeed() {
        val observations = listOf(
            RuntimeObservation(RuntimeBackend.CPU, "HV-1", 8.0, 24.0, 1000, 100, true),
            RuntimeObservation(RuntimeBackend.VULKAN, "HV-1", 20.0, 15.0, 400, 100, true)
        )
        val complexity = listOf(
            BackendComplexity(RuntimeBackend.CPU, 1.0, 0.0),
            BackendComplexity(RuntimeBackend.VULKAN, 2.0, 0.0)
        )
        val result = RuntimeDecisionScorer.score(observations, complexity)
        assertEquals(RuntimeBackend.CPU, result.selected)
    }

    @Test
    fun failedRunsIncreaseBackendFailureRate() {
        val observations = listOf(
            RuntimeObservation(RuntimeBackend.CPU, "HV-1", 8.0, 20.0, 1000, 100, true),
            RuntimeObservation(RuntimeBackend.CPU, "HV-2", 0.0, 0.0, 0, 0, false)
        )
        val complexity = listOf(BackendComplexity(RuntimeBackend.CPU, 1.0, 0.0))
        val result = RuntimeDecisionScorer.score(observations, complexity)
        assertEquals(0.5, result.scores.single().failureRate, 0.0001)
    }
}
