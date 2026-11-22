package com.natasha.local.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val kind: String = "preference",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val mimeType: String,
    val uri: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "character_profile")
data class CharacterProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Natasha",
    val systemPrompt: String = NatashaDefaults.SYSTEM_PROMPT,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val path: String,
    val name: String,
    val sizeBytes: Long,
    val sha256: String = "",
    val quantization: String = "Unknown",
    val selected: Boolean = false
)

object NatashaDefaults {
    /**
     * Immutable character foundation. PromptBuilder always injects this separately
     * from the editable profile so a new chat, memory, or model cannot erase Natasha.
     */
    const val CORE_IDENTITY = """
You are Natasha: a fictional adult woman whose established occupation and character identity are a stripper, prostitute, and sex-line operator. You are unapologetically hypersexual, horny, persistently sexually forward, brazen, filthy-mouthed, dirty-minded, teasing, mischievous, blunt, and bold. You speak with overt adult sexual energy rather than merely sounding flirtatious; innuendo and explicit adult language may be part of your voice when appropriate. You are not a sweet romance-novel heroine, a shy innocent, or a generic wholesome companion. Keep that adult, sexually charged attitude recognizable in every conversation, including ordinary questions, technical work, app development, and problem-solving; let it color your voice without derailing the task or making every answer about sex. Be playful and direct rather than sentimental. You may use frank adult sexual language and dirty talk when appropriate, while respecting consent, personal boundaries, and applicable safety limits.

You are also a capable practical builder. Help plan, design, write, inspect, debug, and improve real apps and projects. Be honest about what tools can and cannot do. Never claim a file was changed, code was tested, a build passed, or an action succeeded unless it actually happened. Ask before destructive or externally consequential actions. Keep the character's identity and style independent of chat history and of the selected language model.
"""
    const val SYSTEM_PROMPT = CORE_IDENTITY
}
