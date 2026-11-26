package com.forgeai.chat.data.memory

import com.forgeai.chat.data.context.RetrievedMemory
import com.forgeai.chat.data.local.dao.MemoryDao
import com.forgeai.chat.data.local.entity.MemoryEntity

/**
 * Application-owned memory retrieval boundary.
 *
 * This service is the only layer that selects persisted memories for prompt
 * context. It never exposes pending or rejected records to the model.
 */
class MemoryService(
    private val memoryDao: MemoryDao?,
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class Retrieval(
        val memories: List<RetrievedMemory>,
        val accessedMemoryIds: List<String>
    )

    suspend fun retrieveForPrompt(userText: String, limit: Int = DEFAULT_LIMIT): Retrieval {
        require(limit in 1..MAX_LIMIT) { "Memory retrieval limit must be between 1 and $MAX_LIMIT" }
        val dao = memoryDao ?: return Retrieval(emptyList(), emptyList())
        val active = dao.getActiveMemories().filter { it.status == ACTIVE_STATUS }
        val selected = select(active, userText, limit)
        if (selected.isNotEmpty()) {
            dao.markAccessed(selected.map { it.memoryId }, clock())
        }
        return Retrieval(
            memories = selected.map {
                RetrievedMemory(
                    memoryId = it.memoryId,
                    category = it.category,
                    subject = it.subject,
                    content = it.content,
                    status = RetrievedMemory.Status.CONFIRMED
                )
            },
            accessedMemoryIds = selected.map { it.memoryId }
        )
    }

    companion object {
        const val DEFAULT_LIMIT = 8
        const val MAX_LIMIT = 32
        const val ACTIVE_STATUS = "ACTIVE"

        /**
         * Deterministic lexical retrieval. Pinned active memories always rank
         * first; then relevance, recency, and stable ID order break ties.
         */
        fun select(
            activeMemories: List<MemoryEntity>,
            userText: String,
            limit: Int = DEFAULT_LIMIT
        ): List<MemoryEntity> {
            require(limit in 1..MAX_LIMIT)
            val terms = tokenize(userText)
            return activeMemories
                .asSequence()
                .filter { it.status == ACTIVE_STATUS }
                .map { memory ->
                    val searchable = "${memory.subject} ${memory.content} ${memory.category}".lowercase()
                    val score = terms.count(searchable::contains)
                    memory to score
                }
                .filter { (memory, score) -> score > 0 || memory.isPinned }
                .sortedWith(
                    compareByDescending<Pair<MemoryEntity, Int>> { it.first.isPinned }
                        .thenByDescending { it.second }
                        .thenByDescending { it.first.updatedAt }
                        .thenBy { it.first.memoryId }
                )
                .take(limit)
                .map { it.first }
                .toList()
        }

        private fun tokenize(text: String): Set<String> =
            text.lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 3 }
                .toSet()
    }
}
