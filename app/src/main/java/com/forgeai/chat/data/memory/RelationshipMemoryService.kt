package com.forgeai.chat.data.memory

import com.forgeai.chat.data.context.RetrievedMemory
import com.forgeai.chat.data.local.dao.LongTermMemoryDao
import com.forgeai.chat.data.local.entity.MemoryEventEntity

/**
 * Retrieves only explicitly approved relationship milestones for persona context.
 * Generic episodic events and pending/rejected candidates are never promoted.
 */
class RelationshipMemoryService(
    private val longTermMemoryDao: LongTermMemoryDao?,
    private val limit: Int = DEFAULT_LIMIT
) {
    init {
        require(limit in 1..MAX_LIMIT)
    }

    suspend fun retrieveForPrompt(): List<RetrievedMemory> {
        val events = longTermMemoryDao?.getActiveRelationshipEvents(limit).orEmpty()
        return mapApprovedEvents(events)
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
        const val EVENT_TYPE = "RELATIONSHIP_MILESTONE"

        fun mapApprovedEvents(events: List<MemoryEventEntity>): List<RetrievedMemory> =
            events.asSequence()
                .filter { it.status == "ACTIVE" && it.eventType == EVENT_TYPE }
                .filter { it.narrativeSnippet.isNotBlank() }
                .map { event ->
                    RetrievedMemory(
                        memoryId = event.eventId,
                        category = "relationship_milestone",
                        subject = event.relatedEntityId?.takeIf(String::isNotBlank)
                            ?: "Established relationship",
                        content = event.narrativeSnippet.trim(),
                        status = RetrievedMemory.Status.CONFIRMED
                    )
                }
                .toList()
    }
}
