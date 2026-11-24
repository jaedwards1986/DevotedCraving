package com.forgeai.chat.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionPerformanceMetricsTest {
    @Test fun highVarianceConfigurationsHaveStableIds() {
        val configs = SamplerConfigurationIds.highVarianceMatrix()
        assertEquals(listOf("HV-1", "HV-2", "HV-3", "HV-4"), configs.map { it.first })
        assertTrue(configs.all { it.second.validate().isEmpty() })
    }

    @Test fun everyTokenTraceRetainsItsSamplerConfiguration() {
        val config = SamplerConfigurationIds.highVarianceMatrix().first()
        val token = GeneratedTokenTrace(
            tokenId = 128006,
            tokenIndex = 0,
            configurationId = config.first,
            sampler = config.second
        )
        assertEquals("HV-1", token.configurationId)
        assertEquals(config.second, token.sampler)
    }

    @Test fun memoryDecayMeasuresContradictionRateAcrossContext() {
        val summary = MemoryDecayScorer.summarize(
            listOf(
                MemoryIntegritySample(0.25, 10, 0),
                MemoryIntegritySample(0.50, 10, 1),
                MemoryIntegritySample(0.90, 10, 4)
            )
        )
        assertEquals(0.0, summary.initialContradictionRate, 0.0001)
        assertEquals(0.4, summary.finalContradictionRate, 0.0001)
        assertEquals(0.6153846, summary.decayRatePerFullContext, 0.0001)
        assertEquals(3, summary.sampleCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun memoryDecayRejectsDuplicateContextFractions() {
        MemoryDecayScorer.summarize(
            listOf(MemoryIntegritySample(0.5, 4, 0), MemoryIntegritySample(0.5, 4, 1))
        )
    }
}
