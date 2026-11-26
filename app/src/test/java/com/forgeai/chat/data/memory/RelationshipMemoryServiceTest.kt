package com.forgeai.chat.data.memory

import com.forgeai.chat.data.context.PersonaOrchestrator
import com.forgeai.chat.data.context.PersonaSpecification
import com.forgeai.chat.data.context.RetrievedMemory
import com.forgeai.chat.data.local.entity.MemoryEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipMemoryServiceTest {
    @Test
    fun onlyApprovedRelationshipMilestonesEnterPersonaContext() {
        val approved = MemoryEventEntity(
            eventId = "approved",
            narrativeSnippet = "They agreed to a shared fictional milestone.",
            relatedEntityId = "primary-persona",
            eventType = RelationshipMemoryService.EVENT_TYPE,
            status = "ACTIVE"
        )
        val generic = approved.copy(
            eventId = "generic",
            eventType = "GENERAL"
        )
        val pending = approved.copy(
            eventId = "pending",
            status = "PENDING_CONSENT"
        )
        val rejected = approved.copy(
            eventId = "rejected",
            status = "REJECTED"
        )

        val result = RelationshipMemoryService.mapApprovedEvents(
            listOf(approved, generic, pending, rejected)
        )

        assertEquals(1, result.size)
        assertEquals("approved", result.single().memoryId)
        assertEquals("relationship_milestone", result.single().category)
        assertEquals("primary-persona", result.single().subject)
        assertEquals(
            RetrievedMemory.Status.CONFIRMED,
            result.single().status
        )

        val personaContext = PersonaOrchestrator.assemble(
            persona = PersonaSpecification(
                revision = "pf04-test-v1",
                canonicalPrompt = "Canonical fictional persona."
            ),
            memories = result
        )
        org.junit.Assert.assertTrue(
            personaContext.contains("They agreed to a shared fictional milestone.")
        )
    }
}
