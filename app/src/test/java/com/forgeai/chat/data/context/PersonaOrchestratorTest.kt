package com.forgeai.chat.data.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaOrchestratorTest {

    private val persona = PersonaSpecification(
        revision = "test-v1",
        canonicalPrompt = "Canonical fictional adult persona."
    )

    @Test
    fun identicalApplicationStateProducesIdenticalCanonicalContext() {
        val memories = listOf(
            RetrievedMemory("m1", "continuity", "Prior event", "A shared fictional event.", RetrievedMemory.Status.CONFIRMED)
        )
        val preferences = listOf(
            PreferenceFact("p1", "style", "Tone", "Warm and playful")
        )

        val first = PersonaOrchestrator.assemble(persona, memories, preferences)
        val second = PersonaOrchestrator.assemble(persona, memories, preferences)

        assertEquals(first, second)
    }

    @Test
    fun unconfirmedMemoriesNeverEnterCanonicalContext() {
        val memories = listOf(
            RetrievedMemory("active", "continuity", "Confirmed", "Approved fact.", RetrievedMemory.Status.CONFIRMED),
            RetrievedMemory("pending", "continuity", "Pending", "Unapproved fact.", RetrievedMemory.Status.CANDIDATE),
            RetrievedMemory("rejected", "continuity", "Rejected", "Rejected fact.", RetrievedMemory.Status.REJECTED)
        )

        val context = PersonaOrchestrator.assemble(persona, memories)

        assertTrue(context.contains("Approved fact."))
        assertFalse(context.contains("Unapproved fact."))
        assertFalse(context.contains("Rejected fact."))
    }

    @Test
    fun preferenceIndexIsStableAndIndependentOfInputOrdering() {
        val a = PreferenceFact("b", "style", "Voice", "Concise")
        val b = PreferenceFact("a", "appearance", "Character", "Brunette")
        val first = PersonaOrchestrator.assemble(persona, emptyList(), listOf(a, b))
        val second = PersonaOrchestrator.assemble(persona, emptyList(), listOf(b, a))

        assertEquals(first, second)
        assertTrue(first.indexOf("Brunette") < first.indexOf("Concise"))
    }

    @Test
    fun validatedRescueInstructionIsOptionalAndDoesNotChangeStoredPersona() {
        val baseline = PersonaOrchestrator.assemble(persona, emptyList())
        val corrected = PersonaOrchestrator.assemble(
            persona,
            emptyList(),
            validatedRescueInstruction = "Return to the established character voice while honoring the latest user request."
        )

        assertFalse(baseline.contains("PERSONA FIDELITY CORRECTION"))
        assertTrue(corrected.contains("PERSONA FIDELITY CORRECTION"))
        assertEquals("Canonical fictional adult persona.", persona.canonicalPrompt)
    }
}
