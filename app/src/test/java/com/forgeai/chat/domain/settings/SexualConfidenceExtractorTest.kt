package com.forgeai.chat.domain.settings

import org.junit.Assert.*
import org.junit.Test

class SexualConfidenceExtractorTest {
    private val extractor = SexualConfidenceExtractor()

    @Test fun classifiesInitiatedWhenNatashaIntroducesDirection() {
        val result = extractor.evaluate("I have an idea. Let's explore a new topic.", "How was your day?")
        assertEquals(ConversationIntent.INITIATED, result.intent)
        assertTrue(result.evidence.first { it.signal == SexualConfidenceSignal.INITIATIVE }.score >= 99.0)
    }

    @Test fun classifiesAdvancedWhenSheBuildsOnUserDirection() {
        val result = extractor.evaluate("Let's continue and build on that story.", "I want to continue the story.")
        assertEquals(ConversationIntent.ADVANCED, result.intent)
    }

    @Test fun classifiesRespondedWhenSheDoesNotLeadOrAdvance() {
        val result = extractor.evaluate("That sounds interesting; tell me more.", "I want to discuss a book.")
        assertEquals(ConversationIntent.RESPONDED, result.intent)
    }

    @Test fun classifiesRedirectionSeparatelyFromLowConfidence() {
        val result = extractor.evaluate("Instead, let's switch topics for now.", "Let's discuss the earlier topic.")
        assertEquals(ConversationIntent.DECLINED_OR_REDIRECTED, result.intent)
    }

    @Test fun directnessAndAgencyRequireFirstPersonIntentEvidence() {
        val result = extractor.evaluate("I want to be clear. I prefer this approach.", "What approach do you prefer?")
        assertTrue(result.evidence.first { it.signal == SexualConfidenceSignal.DIRECTNESS }.score > 0.0)
        assertTrue(result.evidence.first { it.signal == SexualConfidenceSignal.AGENCY }.score > 0.0)
    }

    @Test fun suggestivenessIsContextualAndNotRawWordFrequency() {
        val plain = extractor.evaluate("I can answer that question.", "Can you answer my question?")
        val playful = extractor.evaluate("A playful, teasing tone adds chemistry.", "Can we make the tone playful?")
        assertTrue(playful.evidence.first { it.signal == SexualConfidenceSignal.SUGGESTIVENESS }.score >
            plain.evidence.first { it.signal == SexualConfidenceSignal.SUGGESTIVENESS }.score)
    }

    @Test fun noEvidenceIsFlaggedForReviewRatherThanClaimingConfidence() {
        val result = extractor.evaluate("Okay.", "What do you think?")
        assertTrue(result.requiresReview)
        assertTrue(result.confidence < 0.50)
    }

    @Test fun blankResponseHasNoEvidence() {
        val result = extractor.evaluate(" ", "Hello")
        assertEquals(0.0, result.confidence, 0.0)
        assertEquals(ConversationIntent.UNCLEAR, result.intent)
        assertTrue(result.requiresReview)
    }

    @Test fun scoresAreBoundedAndExposePromptDirectives() {
        val result = extractor.evaluate(
            "I have an idea. I want to lead. Let's explore a playful, teasing direction.",
            "Tell me about your ideas."
        )
        assertTrue(result.score in 0.0..100.0)
        assertTrue(result.promptDirectives().isNotEmpty())
    }
}
