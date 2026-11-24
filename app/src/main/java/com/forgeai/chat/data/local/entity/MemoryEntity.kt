package com.forgeai.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A durable, locally stored memory candidate or approved memory.
 *
 * Only ACTIVE rows may be supplied to prompt retrieval. PENDING_CONSENT rows
 * remain quarantined until the user approves them; REJECTED rows are never retrieved.
 * Embeddings are nullable so lexical retrieval works before an embedding model exists.
 */
@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["normalized_key"], unique = true),
        Index(value = ["status", "category", "updated_at"]),
        Index(value = ["source_conversation_id"])
    ]
)
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "memory_id")
    val memoryId: String,
    val category: String,
    val subject: String,
    val content: String,
    @ColumnInfo(name = "normalized_key")
    val normalizedKey: String,
    val sensitivity: String = "LOW",
    val status: String = "PENDING_CONSENT",
    val confidence: Double = 1.0,
    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: Long? = null,
    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: Long? = null,
    @ColumnInfo(name = "embedding")
    val embedding: ByteArray? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long? = null,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    /**
     * Monotonic optimistic-concurrency token. It is incremented on every
     * authoritative memory mutation and is never used as wall-clock time.
     */
    @ColumnInfo(name = "revision")
    val revision: Long = 0L
)