package com.forgeai.chat.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAcceptanceRunbookTest {
    @Test
    fun baselineRequiresOneCpuThread() {
        val options = DeviceAcceptanceRunbook.requiredLoadOptions()
        assertEquals(1, options.threads)
    }

    @Test
    fun baselineSamplerMatchesAcceptanceProtocol() {
        assertEquals("CPU-BASELINE-T07-P095-1T", DeviceAcceptanceRunbook.BASELINE_CONFIGURATION_ID)
        assertEquals(5, DeviceAcceptanceRunbook.REQUIRED_RESPONSES_PER_SEQUENCE)
    }

    @Test
    fun stressConfigurationIsExistingHvThree() {
        assertEquals("HV-3", DeviceAcceptanceRunbook.STRESS_CONFIGURATION_ID)
        assertTrue(SamplerConfigurationIds.highVarianceMatrix().any { it.first == "HV-3" })
    }
}
