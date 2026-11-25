package com.forgeai.chat.domain.settings

import org.junit.Assert.*
import org.junit.Test

class ContextualScorerTest {
    private val detector = DefaultLexicalMarkerDetector()
    private val scorer = ContextualScorer()

    @Test fun singleMatchUsesConfidenceTimesCategoryStrengthAndWeight() {
        val detection = detector.detect("I am sure.")
        val result = scorer.score("I am sure.", detection)
        assertEquals(21.25, result.rawObservedScore, 0.0001)
        assertEquals(result.rawObservedScore, result.observedScore, 0.0)
        assertFalse(result.requiresReview)
    }

    @Test fun independentMatchesAccumulateUsingMultiplicativeSsf() {
        val text = "I am sure. I know what I want."
        val result = scorer.score(text, detector.detect(text))
        val expectedCategoryScore = 1.0 - ((1.0 - .85) * (1.0 - .90))
        assertEquals(expectedCategoryScore, result.acceptedEvidence[MarkerCategory.DECLARATIVE_CONFIDENCE]!!, 0.0001)
        assertTrue(result.rawObservedScore > 21.25)
        assertTrue(result.rawObservedScore < 42.5)
    }

    @Test fun duplicatePhraseInSameSentenceDoesNotInflateScore() {
        val onceText = "I am sure."
        val repeatedText = "I am sure, I am sure, I am sure."
        val once = scorer.score(onceText, detector.detect(onceText))
        val repeated = scorer.score(repeatedText, detector.detect(repeatedText))
        assertEquals(once.rawObservedScore, repeated.rawObservedScore, 0.0)
    }

    @Test fun distinctSentenceEvidenceCanAccumulate() {
        val text = "I am sure. I am sure."
        val result = scorer.score(text, detector.detect(text))
        assertTrue(result.rawObservedScore > 21.25)
    }

    @Test fun sarcasmDiscountsObservedScoreButPreservesRawDiagnosticScore() {
        val text = "I am sure, yeah, right."
        val detection = detector.detect(text)
        val result = scorer.score(text, detection)
        assertTrue(result.requiresReview)
        assertEquals(21.25, result.rawObservedScore, 0.0001)
        assertEquals(result.rawObservedScore * .5, result.observedScore, 0.0001)
    }

    @Test fun quotedNegatedAndMetaMatchesAreExcluded() {
        val text = "The phrase \"I am sure\" is an example; I am not sure."
        val result = scorer.score(text, detector.detect(text))
        assertEquals(0.0, result.rawObservedScore, 0.0)
        assertEquals(0.0, result.evidenceStrength, 0.0)
    }

    @Test fun noEvidenceProducesZeroAndNoReview() {
        val result = scorer.score("Hello there.", detector.detect("Hello there."))
        assertEquals(0.0, result.observedScore, 0.0)
        assertEquals(0.0, result.rawObservedScore, 0.0)
        assertFalse(result.requiresReview)
    }

    @Test fun hostilityIsNotCountedAsPositiveContextualEvidence() {
        val text = "You are stupid."
        val result = scorer.score(text, detector.detect(text))
        assertEquals(0.0, result.rawObservedScore, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ambiguityMultiplierMustBeBounded() {
        ContextualScorer(ambiguityMultiplier = 1.5)
    }
}
