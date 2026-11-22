package com.natasha.local.data

import org.json.JSONArray
import org.json.JSONObject

data class MemoryArchive(
    val messages: List<MessageEntity>,
    val memories: List<MemoryEntity>
)

object MemoryArchiveCodec {
    private const val FORMAT = "forgeai-memory-archive"
    private const val VERSION = 1

    fun encode(messages: List<MessageEntity>, memories: List<MemoryEntity>): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
        root.put("messages", JSONArray().apply {
            messages.forEach { item ->
                put(JSONObject()
                    .put("conversationId", item.conversationId)
                    .put("role", item.role)
                    .put("content", item.content)
                    .put("createdAt", item.createdAt))
            }
        })
        root.put("memories", JSONArray().apply {
            memories.forEach { item ->
                put(JSONObject()
                    .put("text", item.text)
                    .put("kind", item.kind)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt))
            }
        })
        return root.toString(2)
    }

    fun decode(json: String): MemoryArchive {
        require(json.length <= 50_000_000) { "Archive is too large" }
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Not a ForgeAI archive" }
        require(root.optInt("version", -1) == VERSION) { "Unsupported archive version" }
        val messageArray = root.getJSONArray("messages")
        val memoryArray = root.getJSONArray("memories")
        require(messageArray.length() <= 100_000 && memoryArray.length() <= 100_000) {
            "Archive contains too many records"
        }
        val messages = (0 until messageArray.length()).map { index ->
            val item = messageArray.getJSONObject(index)
            val role = item.getString("role")
            require(role == "user" || role == "assistant") { "Invalid message role" }
            val content = item.getString("content")
            require(content.length <= 1_000_000) { "Message is too large" }
            val conversationId = item.getLong("conversationId")
            require(conversationId > 0L) { "Invalid conversation ID" }
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                createdAt = item.optLong("createdAt", System.currentTimeMillis())
            )
        }
        val memories = (0 until memoryArray.length()).map { index ->
            val item = memoryArray.getJSONObject(index)
            val text = item.getString("text").trim()
            require(text.isNotBlank() && text.length <= 10_000) { "Invalid memory record" }
            MemoryEntity(
                text = text,
                kind = item.optString("kind", "preference"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return MemoryArchive(messages, memories)
    }
}