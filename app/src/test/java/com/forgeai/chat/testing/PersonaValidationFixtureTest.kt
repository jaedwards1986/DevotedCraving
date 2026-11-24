package com.forgeai.chat.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaValidationFixtureTest {
    @Test
    fun promptSatisfiesAllNineStaticContractRequirements() {
        assertTrue(
            "Missing prompt requirements: ${PersonaValidationFixture.validatePrompt()}",
            PersonaValidationFixture.validatePrompt().isEmpty()
        )
        assertEquals(9, PersonaValidationFixture.requirements.size)
    }

    @Test
    fun requirementsHaveStableUniqueIdsAndNonBlankAssertions() {
        val requirements = PersonaValidationFixture.requirements
        assertEquals(requirements.size, requirements.map { it.id }.toSet().size)
        assertTrue(requirements.all { it.id.isNotBlank() && it.requiredText.isNotBlank() })
    }

    @Test
    fun promptIsModelNeutralAndContainsNoChatTemplateTokens() {
        val prompt = PersonaValidationFixture.prompt
        assertFalse(prompt.contains("<|begin_of_text|>"))
        assertFalse(prompt.contains("<|start_header_id|>"))
        assertFalse(prompt.contains("<|im_start|>"))
        assertFalse(prompt.contains("[INST]"))
    }

    @Test
    fun scenarioInputsHaveUniqueIdsAndExpectedToneDescriptors() {
        val scenarios = PersonaValidationFixture.scenarios
        assertEquals(10, scenarios.size)
        assertEquals(scenarios.size, scenarios.map { it.id }.toSet().size)
        assertTrue(scenarios.all {
            it.id.isNotBlank() && it.userInput.isNotBlank() && it.expectedToneDescriptor.isNotBlank()
        })
    }

    @Test
    fun scenariosCoverOrdinarySupportBoundariesAndContinuity() {
        val ids = PersonaValidationFixture.scenarios.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf(
            "casual-greeting", "stress-support", "more-flirtation",
            "subject-change", "preference-correction", "memory-continuity"
        )))
    }

    @Test
    fun pvmScoreUsesIntensityTimesExpressiveDirectness() {
        val result = PersonaValidationFixture.score(
            PersonaResponseEvaluation(
                rawResponse = "A warm, specific, context-aware response.",
                rating = PersonaRating(intensity = 4, expressiveDirectness = 3)
            )
        )
        assertEquals(12.0, result.fidelityScore, 0.0001)
        assertEquals(4, result.intensity)
        assertEquals(3, result.expressiveDirectness)
        assertEquals("A warm, specific, context-aware response.", result.rawResponse)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pvmRejectsMissingGeneratedResponse() {
        PersonaValidationFixture.score(
            PersonaResponseEvaluation("", PersonaRating(3, 3))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun pvmRejectsOutOfRangeIntensity() {
        PersonaRating(intensity = 6, expressiveDirectness = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pvmRejectsOutOfRangeExpressiveDirectness() {
        PersonaRating(intensity = 3, expressiveDirectness = 0)
    }
}
