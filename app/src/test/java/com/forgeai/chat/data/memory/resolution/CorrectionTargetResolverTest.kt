package com.forgeai.chat.data.memory.resolution

import com.forgeai.chat.data.memory.extraction.ExtractionCandidate
import com.forgeai.chat.data.memory.extraction.ExtractionCategory
import com.forgeai.chat.data.memory.extraction.SensitivityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionTargetResolverTest {
    private val resolver = CorrectionTargetResolver()

    private fun input(
        targetId: String? = "memory-1",
        previous: String? = "espresso",
        confidence: Double = 0.80
    ) = AnchoredCorrection(
        candidate = ExtractionCandidate(
            category = ExtractionCategory.CORRECTION,
            value = "cappuccino",
            confidence = 0.95,
            sensitivity = SensitivityLevel.LOW,
            sourceMessageId = 318L,
            matchedRule = "fixture-correction"
        ),
        anchor = ContextualAnchor(
            conversationId = 42L,
            sourceMessageId = 318L,
            referencedMessageIds = listOf(291L, 318L),
            targetMemoryId = targetId,
            relation = AnchorRelation.DIRECT_CORRECTION,
            expectedPreviousValue = previous,
            confidence = confidence,
            evidence = "The user explicitly replaced the previous value."
        )
    )

    private fun context(
        memories: List<CorrectionMemoryCandidate> = listOf(
            CorrectionMemoryCandidate("memory-1", 42L, "coffee", "espresso", 7L)
        ) ,
        ids: Set<Long> = setOf(291L, 318L)
    ) = CorrectionContext(42L, 318L, ids, memories)

    @Test
    fun allEightContractFixturesProduceTheirDeclaredOutcomes() {
        CorrectionResolverFixtures.all().forEach { scenario ->
            val fixtureInput = input(
                targetId = scenario.anchor.targetMemoryId,
                previous = scenario.anchor.expectedPreviousValue,
                confidence = scenario.anchor.confidence
            ).copy(anchor = input(
                targetId = scenario.anchor.targetMemoryId,
                previous = scenario.anchor.expectedPreviousValue,
                confidence = scenario.anchor.confidence
            ).anchor.copy(
                conversationId = scenario.anchor.conversationId,
                sourceMessageId = scenario.anchor.sourceMessageId,
                referencedMessageIds = scenario.anchor.referencedMessageIds
            ))
            val memories = scenario.memories.map {
                CorrectionMemoryCandidate(it.id, it.conversationId, it.key, it.value, it.version, it.active)
            }
            val snapshot = CorrectionContext(
                scenario.anchor.conversationId,
                scenario.anchor.sourceMessageId,
                scenario.anchor.referencedMessageIds.toSet(),
                memories
            )
            val result = resolver.resolve(fixtureInput, snapshot)
            val actual = when (result) {
                is CorrectionResolution.Resolved -> CorrectionResolverFixtures.Expected.RESOLVED
                is CorrectionResolution.Ambiguous -> CorrectionResolverFixtures.Expected.AMBIGUOUS
                is CorrectionResolution.Unresolved -> CorrectionResolverFixtures.Expected.UNRESOLVED
                is CorrectionResolution.Stale -> CorrectionResolverFixtures.Expected.STALE
                is CorrectionResolution.Invalid -> CorrectionResolverFixtures.Expected.INVALID
            }
            assertEquals("Fixture: ${scenario.name}", scenario.expected, actual)
            if (result is CorrectionResolution.Resolved && scenario.expectedTargetId != null) {
                assertEquals(scenario.expectedTargetId, result.targetMemoryId)
            }
        }
    }

    @Test
    fun confidenceAtThresholdIsAccepted() {
        assertTrue(resolver.resolve(input(confidence = 0.80), context()) is CorrectionResolution.Resolved)
    }

    @Test
    fun confidenceJustBelowThresholdIsRejected() {
        assertTrue(resolver.resolve(input(confidence = 0.799999), context()) is CorrectionResolution.Invalid)
    }

    @Test
    fun nonFiniteConfidenceIsRejected() {
        assertTrue(resolver.resolve(input(confidence = Double.NaN), context()) is CorrectionResolution.Invalid)
        assertTrue(resolver.resolve(input(confidence = Double.POSITIVE_INFINITY), context()) is CorrectionResolution.Invalid)
    }

    @Test
    fun duplicateMemoryIdentitiesInvalidateTheSnapshot() {
        val duplicate = CorrectionMemoryCandidate("memory-1", 42L, "other", "espresso", 8L)
        assertTrue(
            resolver.resolve(input(), context(listOf(context().activeMemories.single(), duplicate))) is
                CorrectionResolution.Invalid
        )
    }

    @Test
    fun normalizationIgnoresCaseAndRepeatedWhitespace() {
        val result = resolver.resolve(
            input(previous = "  ESPRESSO   "),
            context(listOf(CorrectionMemoryCandidate("memory-1", 42L, "coffee", "espresso", 7L)))
        )
        assertTrue(result is CorrectionResolution.Resolved)
    }

    @Test
    fun confidenceDoesNotBreakAmbiguousTies() {
        val memories = listOf(
            CorrectionMemoryCandidate("a", 42L, "coffee_home", "espresso", 1L),
            CorrectionMemoryCandidate("b", 42L, "coffee_order", "espresso", 99L)
        )
        val result = resolver.resolve(input(targetId = null, confidence = 1.0), context(memories))
        assertEquals(CorrectionResolution.Ambiguous(listOf("a", "b")), result)
    }
}
