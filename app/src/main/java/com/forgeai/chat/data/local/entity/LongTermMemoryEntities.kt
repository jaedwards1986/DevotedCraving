package com.forgeai.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Normalized low-risk preference memory. The extractor must classify sensitivity
 * before choosing ACTIVE; sensitive candidates must remain PENDING_CONSENT.
 */
@Entity(
    tableName = "user_preferences",
    indices = [
        Index(value = ["status", "updated_at"]),
        Index(value = ["sensitivity", "status"])
    ]
)
data class UserPreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "setting_name")
    val settingName: String,
    @ColumnInfo(name = "setting_value")
    val settingValue: String,
    val metadata: String? = null,
    val sensitivity: String = "LOW",
    val status: String = "PENDING_CONSENT",
    val confidence: Double = 1.0,
    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: Long? = null,
    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: Long? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A durable episodic fact or event. relatedEntityId is deliberately an opaque
 * identifier: the app has no canonical entity table yet, so no speculative FK.
 */
@Entity(
    tableName = "memory_events",
    indices = [
        Index(value = ["related_entity_id"]),
        Index(value = ["status", "created_at"]),
        Index(value = ["source_conversation_id"])
    ]
)
data class MemoryEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "narrative_snippet")
    val narrativeSnippet: String,
    @ColumnInfo(name = "related_entity_id")
    val relatedEntityId: String? = null,
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Double = 1.0,
    val sensitivity: String = "LOW",
    val status: String = "PENDING_CONSENT",
    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: Long? = null,
    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Audit trail for explicit corrections. Old/new values are retained as an
 * append-only record; memoryId is not an FK so deleting a memory need not erase
 * the explanation of a prior correction.
 */
@Entity(
    tableName = "correction_records",
    indices = [
        Index(value = ["memory_id", "corrected_at"]),
        Index(value = ["source_conversation_id"])
    ]
)
data class CorrectionRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "correction_id")
    val correctionId: String,
    @ColumnInfo(name = "memory_id")
    val memoryId: String,
    @ColumnInfo(name = "previous_value")
    val previousValue: String,
    @ColumnInfo(name = "corrected_value")
    val correctedValue: String,
    val reason: String? = null,
    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: Long? = null,
    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: Long? = null,
    @ColumnInfo(name = "corrected_at")
    val correctedAt: Long = System.currentTimeMillis()
)
