package com.forgeai.chat.data.memory.resolution

/**
 * Supplies a bounded snapshot. Production implementations may read recent messages and
 * active memories; unit tests provide deterministic in-memory snapshots.
 */
interface CorrectionContextProvider {
    suspend fun getContext(
        conversationId: Long,
        sourceMessageId: Long
    ): CorrectionContext
}

data class CorrectionContext(
    val conversationId: Long,
    val sourceMessageId: Long,
    val availableMessageIds: Set<Long>,
    val activeMemories: List<CorrectionMemoryCandidate>
)

data class CorrectionMemoryCandidate(
    val memoryId: String,
    val conversationId: Long,
    val normalizedKey: String,
    val currentValue: String,
    val version: Long,
    val active: Boolean = true
)
