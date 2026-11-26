package com.forgeai.chat.data.memory

import com.forgeai.chat.data.local.entity.MemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryServiceTest {
    private fun memory(
        id: String,
        subject: String = "subject",
        content: String = "content",
        status: String = "ACTIVE",
        pinned: Boolean = false,
        updatedAt: Long = 1L
    ) = MemoryEntity(
        memoryId = id,
        category = "continuity",
        subject = subject,
        content = content,
        normalizedKey = id,
        status = status,
        isPinned = pinned,
        updatedAt = updatedAt
    )

    @Test fun pendingAndRejectedMemoriesAreNeverSelected() {
        val rows = listOf(
            memory("active", subject = "coffee"),
            memory("pending", subject = "coffee", status = "PENDING_CONSENT"),
            memory("rejected", subject = "coffee", status = "REJECTED")
        )
        assertEquals(listOf("active"), MemoryService.select(rows, "coffee").map { it.memoryId })
    }

    @Test fun pinnedMemoriesAreIncludedWithoutLexicalMatch() {
        val rows = listOf(memory("pinned", pinned = true), memory("unrelated"))
        assertEquals(listOf("pinned"), MemoryService.select(rows, "weather").map { it.memoryId })
    }

    @Test fun relevanceThenRecencyThenStableIdDetermineOrder() {
        val rows = listOf(
            memory("z", subject = "coffee", updatedAt = 10),
            memory("a", subject = "coffee", updatedAt = 10),
            memory("recent", subject = "coffee tea", updatedAt = 1)
        )
        assertEquals(
            listOf("recent", "a", "z"),
            MemoryService.select(rows, "coffee tea").map { it.memoryId }
        )
    }

    @Test fun selectionIsBoundedByRequestedLimit() {
        val rows = (1..12).map { memory("id-${it}", subject = "coffee") }
        assertEquals(5, MemoryService.select(rows, "coffee", limit = 5).size)
    }

    @Test fun emptyQueryReturnsOnlyPinnedMemories() {
        val rows = listOf(memory("ordinary"), memory("pinned", pinned = true))
        assertEquals(listOf("pinned"), MemoryService.select(rows, "").map { it.memoryId })
    }
}
