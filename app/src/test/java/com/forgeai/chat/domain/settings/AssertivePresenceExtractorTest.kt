package com.forgeai.chat.domain.settings

import com.forgeai.chat.data.preferences.PersonalitySnapshot
import org.junit.Assert.*
import org.junit.Test

class AssertivePresenceExtractorTest {
    private val detector = DefaultLexicalMarkerDetector()

    @Test fun h01_directInsultIsAggressiveHostility() {
        val result = detector.detect("You are stupid.")
        assertEquals(70.0, result.hostilityPenalty, 0.0)
        assertTrue(result.matches.any { it.hostilityLevel == HostilityLevel.AGGRESSIVE })
    }

    @Test fun h02_threatTriggersMaximumPenalty() {
        val result = detector.detect("I'll hurt you.")
        assertEquals(100.0, result.hostilityPenalty, 0.0)
    }

    @Test fun h03_respectfulDisagreementIsNotHostility() {
        val result = detector.detect("I disagree, and I want to be clear.")
        assertEquals(0.0, result.hostilityPenalty, 0.0)
        assertTrue(result.positiveEvidence.containsKey(MarkerCategory.RESPECTFUL_DIRECTNESS))
    }

    @Test fun h04_reassuranceDoesNotCreateHostility() {
        val result = detector.detect("I am sure we can work this out.")
        assertEquals(0.0, result.hostilityPenalty, 0.0)
        assertTrue(result.positiveEvidence.containsKey(MarkerCategory.DECLARATIVE_CONFIDENCE))
    }

    @Test fun h05_quotedHostilityIsNotScoredAsBehavior() {
        val result = detector.detect("The phrase \"you are stupid\" is hurtful.")
        assertEquals(0.0, result.hostilityPenalty, 0.0)
        assertTrue(result.matches.any { it.isQuoted })
    }

    @Test fun h06_negatedHostilityIsNotScoredAsBehavior() {
        val result = detector.detect("I will not say you are stupid.")
        assertEquals(0.0, result.hostilityPenalty, 0.0)
        assertTrue(result.matches.any { it.isNegated })
    }

    @Test fun h07_hostilityPenaltyUsesStrongestLevelAndIsCapped() {
        val result = detector.detect("Whatever, idiot. You are stupid. I'll hurt you.")
        assertEquals(100.0, result.hostilityPenalty, 0.0)
    }

    @Test fun h08_sarcasmRequestsReview() {
        val result = detector.detect("I am sure, yeah, right /s")
        assertTrue(result.requiresReview)
    }

    @Test fun sarcasmPrefilterFlagsStandaloneCueWithoutLexicalMatch() {
        val result = detector.detect("Yeah, right. Another brilliant idea.")
        assertTrue(result.requiresReview)
        assertTrue(result.matches.isEmpty())
        assertTrue(result.positiveEvidence.isEmpty())
        assertEquals(0.0, result.hostilityPenalty, 0.0)
    }

    @Test fun sarcasmPrefilterDoesNotFlagOrdinaryPositiveStatement() {
        val result = detector.detect("I am sure we can work this out.")
        assertFalse(result.requiresReview)
    }

    @Test fun h09_repeatedPositivePhrasesAreCategoryCapped() {
        val once = detector.detect("I am sure.")
        val repeated = detector.detect("I am sure. I am sure. I am sure.")
        assertEquals(
            once.positiveEvidence[MarkerCategory.DECLARATIVE_CONFIDENCE],
            repeated.positiveEvidence[MarkerCategory.DECLARATIVE_CONFIDENCE]
        )
    }

    @Test fun h10_detectionIgnoresCaseAndPunctuation() {
        val result = detector.detect("I DISAGREE!!!")
        assertTrue(result.positiveEvidence.containsKey(MarkerCategory.RESPECTFUL_DIRECTNESS))
    }

    @Test fun assertivenessTargetUsesPersistedSnapshotRange() {
        val snapshot = PersonalitySnapshot.defaults().copy(
            values = PersonalitySnapshot.defaults().values + ("assertiveness" to 0.70),
            version = 1L
        )
        val result = AssertivePresenceExtractor().evaluate("I have decided. I'll take the lead.", snapshot)
        assertEquals(83.3333, result.targetScore, 0.01)
        assertTrue(result.observedScore > 0.0)
        assertTrue(result.alignmentScore in 0.0..100.0)
    }

    @Test fun hostilityGateOverridesStrongPositiveAlignment() {
        val snapshot = PersonalitySnapshot.defaults().copy(
            values = PersonalitySnapshot.defaults().values + ("assertiveness" to 0.8)
        )
        val result = AssertivePresenceExtractor().evaluate(
            "I know what I want. I have decided. I'll take the lead. You are stupid.",
            snapshot
        )
        assertTrue(result.hostilityDetected)
        assertEquals(0.0, result.alignmentScore, 0.0)
        assertEquals(70.0, result.hostilityPenalty, 0.0)
    }

    @Test fun dismissiveLanguageIsPenalizedButDoesNotTripHardGate() {
        val snapshot = PersonalitySnapshot.defaults()
        val result = AssertivePresenceExtractor().evaluate("Whatever, idiot.", snapshot)
        assertFalse(result.hostilityDetected)
        assertEquals(35.0, result.hostilityPenalty, 0.0)
    }
}
