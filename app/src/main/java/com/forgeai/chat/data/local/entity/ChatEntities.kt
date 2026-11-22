package com.forgeai.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromWire(value: String): MessageRole =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ASSISTANT
    }
}

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updated_at"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    @ColumnInfo(name = "model_name")
    val modelName: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id", "created_at"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    val role: String,
    val content: String,
    @ColumnInfo(name = "model_name")
    val modelName: String = "",
    @ColumnInfo(name = "is_error")
    val isError: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

data class ConversationWithPreview(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "last_message")
    val lastMessage: String?,
    @ColumnInfo(name = "message_count")
    val messageCount: Int
)
