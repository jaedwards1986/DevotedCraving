package com.natasha.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryArchiveCodecTest {
    @Test fun roundTripsMessagesAndMemories() {
        val messages = listOf(
            MessageEntity(conversationId = 4, role = "user", content = "Hello", createdAt = 10),
            MessageEntity(conversationId = 4, role = "assistant", content = "Hi", createdAt = 11),
            MessageEntity(conversationId = 9, role = "user", content = "Second chat", createdAt = 12)
        )
        val memories = listOf(MemoryEntity(text = "Prefers concise answers", kind = "pinned", createdAt = 2, updatedAt = 3))
        val restored = MemoryArchiveCodec.decode(MemoryArchiveCodec.encode(messages, memories))
        assertEquals(messages.map { it.conversationId to it.content }, restored.messages.map { it.conversationId to it.content })
        assertEquals(memories.map { it.text to it.kind }, restored.memories.map { it.text to it.kind })
    }

    @Test fun rejectsWrongArchiveFormat() {
        val error = runCatching { MemoryArchiveCodec.decode("""{"format":"other","version":1,"messages":[],"memories":[]}""") }.exceptionOrNull()
        assertTrue(error != null)
    }

    @Test fun rejectsInvalidConversationIds() {
        val json = """{"format":"forgeai-memory-archive","version":1,"messages":[{"conversationId":0,"role":"user","content":"x","createdAt":1}],"memories":[]}"""
        val error = runCatching { MemoryArchiveCodec.decode(json) }.exceptionOrNull()
        assertTrue(error != null)
    }
}
