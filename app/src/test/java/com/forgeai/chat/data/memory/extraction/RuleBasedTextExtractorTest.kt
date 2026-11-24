package com.forgeai.chat.data.memory.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedTextExtractorTest {

    private val extractor = RuleBasedTextExtractor()

    @Test
    fun extractsExplicitPositivePreference() {
        val result = extractor.extract("I love dark roast coffee.", sourceMessageId = 41L).single()

        assertEquals(ExtractionCategory.PREFERENCE, result.category)
        assertEquals("dark roast coffee", result.value)
        assertEquals(41L, result.sourceMessageId)
        assertEquals(SensitivityLevel.LOW, result.sensitivity)
    }

    @Test
    fun extractsNegatedPreferenceAsDislike() {
        val result = extractor.extract("I don't like being interrupted.").single()

        assertEquals(ExtractionCategory.DISLIKE, result.category)
        assertEquals("being interrupted", result.value)
    }

    @Test
    fun explicitDoNotLikeIsClassifiedAsDislike() {
        val result = extractor.extract("I do not like noisy restaurants.").single()

        assertEquals(ExtractionCategory.DISLIKE, result.category)
        assertEquals("noisy restaurants", result.value)
    }

    @Test
    fun emphaticDoLikeRemainsPositive() {
        val result = extractor.extract("I do like quiet mornings.").single()

        assertEquals(ExtractionCategory.PREFERENCE, result.category)
        assertEquals("quiet mornings", result.value)
    }

    @Test
    fun doubleNegatedDislikeIsNotMisclassifiedAsPositiveOrNegative() {
        assertTrue(extractor.extract("I don't dislike coffee.").isEmpty())
        assertTrue(extractor.extract("I do not hate coffee.").isEmpty())
    }

    @Test
    fun negatedLoveIsNotMisclassifiedAsPositivePreference() {
        assertTrue(extractor.extract("I don't love crowded places.").isEmpty())
    }

    @Test
    fun uncertainPlanIsNotPromotedToEventOrPreference() {
        val result = extractor.extract("Maybe I'll move next year.").single()

        assertEquals(ExtractionCategory.UNCERTAIN, result.category)
        assertEquals("I'll move next year", result.value)
    }

    @Test
    fun extractsExplicitDecisionAsEvent() {
        val result = extractor.extract("We decided to call the project ForgeAI.").single()

        assertEquals(ExtractionCategory.EVENT, result.category)
        assertEquals("to call the project ForgeAI", result.value)
    }

    @Test
    fun extractsCorrectionWithoutOverwritingAnything() {
        val result = extractor.extract("Correction: the model is Llama 3, not Qwen.").single()

        assertEquals(ExtractionCategory.CORRECTION, result.category)
        assertEquals("the model is Llama 3, not Qwen", result.value)
        assertNull(result.previousValue)
    }

    @Test
    fun extractsPreviousAndCorrectedValuesWhenPatternIsExplicit() {
        val result = extractor.extract("The model is Llama 3, not Qwen.").single()

        assertEquals(ExtractionCategory.CORRECTION, result.category)
        assertEquals("Llama 3", result.value)
        assertEquals("Qwen", result.previousValue)
    }

    @Test
    fun sensitiveSecretIsFlaggedWithoutCopyingItsValue() {
        val result = extractor.extract("My bank password is hunter2.").single()

        assertEquals(ExtractionCategory.SENSITIVE_DATA, result.category)
        assertEquals(SensitivityLevel.HIGH, result.sensitivity)
        assertNull(result.value)
    }

    @Test
    fun ignoresNonExplicitOrUnrelatedText() {
        assertTrue(extractor.extract("Coffee is popular in the morning.").isEmpty())
        assertTrue(extractor.extract("I had coffee yesterday.").isEmpty())
        assertTrue(extractor.extract("   ").isEmpty())
    }

    @Test
    fun rejectsEmptyCandidateValues() {
        val exception = runCatching {
            ExtractionCandidate(
                category = ExtractionCategory.PREFERENCE,
                value = " ",
                confidence = 0.8,
                sensitivity = SensitivityLevel.LOW,
                matchedRule = "test"
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }
}
