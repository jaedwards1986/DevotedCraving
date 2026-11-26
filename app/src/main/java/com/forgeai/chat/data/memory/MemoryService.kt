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

    enum class MutationResult {
        UPDATED,
        NOT_FOUND,
        NOT_PENDING,
        NOT_EDITABLE
    }

    /** Explicitly approve a candidate. Only PENDING_CONSENT rows can transition. */
    suspend fun approve(memoryId: String): MutationResult {
        val dao = memoryDao ?: return MutationResult.NOT_FOUND
        val changed = dao.approve(memoryId, clock())
        return if (changed == 1) MutationResult.UPDATED else {
            if (dao.getById(memoryId) == null) MutationResult.NOT_FOUND else MutationResult.NOT_PENDING
        }
    }

    /** Reject a candidate without deleting its audit/history row. */
    suspend fun reject(memoryId: String): MutationResult {
        val dao = memoryDao ?: return MutationResult.NOT_FOUND
        val changed = dao.reject(memoryId, clock())
        return if (changed == 1) MutationResult.UPDATED else {
            if (dao.getById(memoryId) == null) MutationResult.NOT_FOUND else MutationResult.NOT_PENDING
        }
    }

    /**
     * Correct an existing memory in place. Consent status and provenance are
     * preserved; correcting a pending candidate does not silently approve it.
     */
    suspend fun correct(
        memoryId: String,
        category: String,
        subject: String,
        content: String
    ): MutationResult {
        val dao = memoryDao ?: return MutationResult.NOT_FOUND
        val current = dao.getById(memoryId) ?: return MutationResult.NOT_FOUND
        if (current.status != ACTIVE_STATUS && current.status != "PENDING_CONSENT") {
            return MutationResult.NOT_EDITABLE
        }
        val cleanCategory = category.trim()
        val cleanSubject = subject.trim()
        val cleanContent = content.trim()
        require(cleanCategory.isNotEmpty()) { "Memory category must not be blank" }
        require(cleanSubject.isNotEmpty()) { "Memory subject must not be blank" }
        require(cleanContent.isNotEmpty()) { "Memory content must not be blank" }

        dao.upsert(
            current.copy(
                category = cleanCategory,
                subject = cleanSubject,
                content = cleanContent,
                updatedAt = clock()
            )
        )
        return MutationResult.UPDATED
    }

    /** Permanently remove a memory at the user's explicit request. */
    suspend fun delete(memoryId: String): MutationResult {
        val dao = memoryDao ?: return MutationResult.NOT_FOUND
        if (dao.getById(memoryId) == null) return MutationResult.NOT_FOUND
        dao.delete(memoryId)
        return MutationResult.UPDATED
    }

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
