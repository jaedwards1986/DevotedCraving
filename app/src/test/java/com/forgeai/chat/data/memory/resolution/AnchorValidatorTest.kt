package com.forgeai.chat.data.memory.resolution

import com.forgeai.chat.data.memory.extraction.ExtractionCandidate
import com.forgeai.chat.data.memory.extraction.ExtractionCategory
import com.forgeai.chat.data.memory.extraction.SensitivityLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorValidatorTest {
    private class FakeContextProvider(
        private val snapshot: CorrectionContext
    ) : CorrectionContextProvider {
        override suspend fun getContext(
            conversationId: Long,
            sourceMessageId: Long
        ): CorrectionContext = snapshot
    }

    private fun candidate() = ExtractionCandidate(
        category = ExtractionCategory.CORRECTION,
        value = "cappuccino",
        confidence = 0.98,
        sensitivity = SensitivityLevel.LOW,
        sourceMessageId = 318L,
        matchedRule = "correction"
    )

    private fun anchor(
        targetId: String? = "pref-coffee",
        previous: String? = "espresso",
        relation: AnchorRelation = AnchorRelation.DIRECT_CORRECTION,
        confidence: Double = 0.98,
        evidence: String = "Explicit replacement in the source message"
    ) = ContextualAnchor(
        schemaVersion = 1,
        targetMemoryId = targetId,
        conversationId = 42L,
        sourceMessageId = 318L,
        referencedMessageIds = listOf(291L, 318L),
        relation = relation,
        expectedPreviousValue = previous,
        confidence = confidence,
        evidence = evidence
    )

    private fun context(
        memories: List<CorrectionMemoryCandidate> = listOf(
            CorrectionMemoryCandidate("pref-coffee", 42L, "coffee", "espresso", 3L)
        ),
        conversationId: Long = 42L,
        sourceMessageId: Long = 318L,
        messageIds: Set<Long> = setOf(291L, 318L)
    ) = CorrectionContext(conversationId, sourceMessageId, messageIds, memories)

    @Test
    fun resolvesSingleExplicitTargetAndReturnsVersionForWriterRecheck() = runBlocking {
        val validator = AnchorValidator(FakeContextProvider(context()))
        val result = validator.validate(AnchoredCorrection(candidate(), anchor()))
        assertTrue(result is CorrectionResolution.Resolved)
        result as CorrectionResolution.Resolved
        assertEquals("pref-coffee", result.targetMemoryId)
        assertEquals(3L, result.expectedVersion)
    }

    @Test
    fun resolvesUniqueExactPreviousValueWhenModelDoesNotSupplyId() = runBlocking {
        val validator = AnchorValidator(FakeContextProvider(context()))
        val result = validator.validate(AnchoredCorrection(candidate(), anchor(targetId = null)))
        assertTrue(result is CorrectionResolution.Resolved)
        assertEquals("pref-coffee", (result as CorrectionResolution.Resolved).targetMemoryId)
    }

    @Test
    fun returnsAmbiguousWhenPreviousValueMatchesMultipleMemories() = runBlocking {
        val memories = listOf(
            CorrectionMemoryCandidate("coffee-home", 42L, "coffee_home", "espresso", 1L),
            CorrectionMemoryCandidate("coffee-order", 42L, "coffee_order", "espresso", 1L)
        )
        val result = AnchorValidator(FakeContextProvider(context(memories)))
            .validate(AnchoredCorrection(candidate(), anchor(targetId = null)))
        assertEquals(
            listOf("coffee-home", "coffee-order"),
            (result as CorrectionResolution.Ambiguous).candidateMemoryIds
        )
    }

    @Test
    fun rejectsStaleExplicitTarget() = runBlocking {
        val changed = context(listOf(
            CorrectionMemoryCandidate("pref-coffee", 42L, "coffee", "cappuccino", 4L)
        ))
        val result = AnchorValidator(FakeContextProvider(changed))
            .validate(AnchoredCorrection(candidate(), anchor()))
        assertTrue(result is CorrectionResolution.Stale)
    }

    @Test
    fun rejectsTargetFromAnotherConversation() = runBlocking {
        val foreign = context(listOf(
            CorrectionMemoryCandidate("pref-coffee", 99L, "coffee", "espresso", 1L)
        ))
        val result = AnchorValidator(FakeContextProvider(foreign))
            .validate(AnchoredCorrection(candidate(), anchor()))
        assertTrue(result is CorrectionResolution.Invalid)
    }

    @Test
    fun refusesClarificationAndUnknownRelations() = runBlocking {
        for (relation in listOf(AnchorRelation.CLARIFICATION, AnchorRelation.AMBIGUOUS, AnchorRelation.UNKNOWN)) {
            val result = AnchorValidator(FakeContextProvider(context()))
                .validate(AnchoredCorrection(candidate(), anchor(relation = relation)))
            assertTrue(result is CorrectionResolution.Unresolved)
        }
    }

    @Test
    fun rejectsInvalidConfidenceAndMissingEvidence() = runBlocking {
        val validator = AnchorValidator(FakeContextProvider(context()))
        val badConfidence = validator.validate(
            AnchoredCorrection(candidate(), anchor(confidence = 1.2))
        )
        val missingEvidence = validator.validate(
            AnchoredCorrection(candidate(), anchor(evidence = " "))
        )
        assertTrue(badConfidence is CorrectionResolution.Invalid)
        assertTrue(missingEvidence is CorrectionResolution.Invalid)
    }

    @Test
    fun rejectsContextThatDoesNotContainReferencedMessages() = runBlocking {
        val incomplete = context(messageIds = setOf(318L))
        val result = AnchorValidator(FakeContextProvider(incomplete))
            .validate(AnchoredCorrection(candidate(), anchor()))
        assertTrue(result is CorrectionResolution.Invalid)
    }

    @Test
    fun doesNotResolveInactiveOrMissingMemory() = runBlocking {
        val inactive = context(listOf(
            CorrectionMemoryCandidate("pref-coffee", 42L, "coffee", "espresso", 3L, active = false)
        ))
        val inactiveResult = AnchorValidator(FakeContextProvider(inactive))
            .validate(AnchoredCorrection(candidate(), anchor()))
        val missingResult = AnchorValidator(FakeContextProvider(context(emptyList())))
            .validate(AnchoredCorrection(candidate(), anchor()))
        assertTrue(inactiveResult is CorrectionResolution.Unresolved)
        assertTrue(missingResult is CorrectionResolution.Unresolved)
    }
}
