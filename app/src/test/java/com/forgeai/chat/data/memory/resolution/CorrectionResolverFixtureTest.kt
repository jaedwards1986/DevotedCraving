package com.forgeai.chat.data.memory.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the resolver test data itself.
 *
 * These tests intentionally do NOT claim to validate resolution behavior. The production
 * CorrectionTargetResolver will be tested against these scenarios in the next implementation
 * step, once its contract and matching policy are added.
 */
class CorrectionResolverFixtureTest {
    @Test
    fun fixtureSetCoversAllRequiredResolutionBoundaries() {
        val scenarios = CorrectionResolverFixtures.all()

        assertEquals(8, scenarios.size)
        assertEquals(
            setOf(
                CorrectionResolverFixtures.Expected.RESOLVED,
                CorrectionResolverFixtures.Expected.AMBIGUOUS,
                CorrectionResolverFixtures.Expected.UNRESOLVED,
                CorrectionResolverFixtures.Expected.STALE,
                CorrectionResolverFixtures.Expected.INVALID
            ),
            scenarios.map { it.expected }.toSet()
        )
    }

    @Test
    fun everyScenarioHasAUniqueNameAndValidSourceIdentity() {
        val scenarios = CorrectionResolverFixtures.all()

        assertEquals(scenarios.size, scenarios.map { it.name }.toSet().size)
        scenarios.forEach { scenario ->
            assertTrue(scenario.name.isNotBlank())
            assertTrue(scenario.anchor.conversationId > 0L)
            assertTrue(scenario.anchor.sourceMessageId > 0L)
            assertFalse(scenario.anchor.referencedMessageIds.any { it <= 0L })
        }
    }

    @Test
    fun onlyResolvedScenarioDeclaresAnExpectedTarget() {
        CorrectionResolverFixtures.all().forEach { scenario ->
            if (scenario.expected == CorrectionResolverFixtures.Expected.RESOLVED) {
                assertNotNull(scenario.expectedTargetId)
            } else {
                assertEquals(null, scenario.expectedTargetId)
            }
        }
    }

    @Test
    fun explicitTargetFixturesNeverPointOutsideTheirSnapshotExceptInvalidConversationCase() {
        CorrectionResolverFixtures.all()
            .filter { it.anchor.targetMemoryId != null }
            .forEach { scenario ->
                val target = scenario.memories.firstOrNull {
                    it.id == scenario.anchor.targetMemoryId
                }
                if (scenario.expected == CorrectionResolverFixtures.Expected.UNRESOLVED) {
                    // An unresolved explicit target may be absent or present but inactive.
                    assertTrue(target == null || !target.active)
                } else {
                    assertNotNull(target)
                }
            }
    }
}
