package com.forgeai.chat.data.repository

import com.forgeai.chat.data.context.PersonaOrchestrator
import com.forgeai.chat.data.context.PersonaSpecification
import com.forgeai.chat.data.context.RetrievedMemory
import com.forgeai.chat.data.local.dao.ChatDao
import com.forgeai.chat.data.local.dao.MemoryDao
import com.forgeai.chat.data.local.entity.ConversationEntity
import com.forgeai.chat.data.local.entity.ConversationWithPreview
import com.forgeai.chat.data.local.entity.MessageEntity
import com.forgeai.chat.data.local.entity.MessageRole
import com.forgeai.chat.data.preferences.AppSettings
import com.forgeai.chat.data.preferences.SettingsRepository
import com.forgeai.chat.data.preferences.ThemeMode
import com.forgeai.chat.data.remote.LlmStreamClient
import com.forgeai.chat.nativebridge.Llama3PromptBuilder
import com.forgeai.chat.nativebridge.LocalGenerationFlowAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

interface ChatRepository {
    val settingsFlow: Flow<AppSettings>
    val conversationsFlow: Flow<List<ConversationWithPreview>>
    val totalMessageCountFlow: Flow<Int>

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>
    suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity>
    suspend fun getConversation(conversationId: Long): ConversationEntity?
    suspend fun createConversation(title: String, modelName: String): Long
    suspend fun renameConversation(conversationId: Long, newTitle: String)
    suspend fun deleteConversation(conversationId: Long)
    suspend fun deleteAllConversations()
    suspend fun saveMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        modelName: String = "",
        isError: Boolean = false
    ): Long

    suspend fun deleteMessage(messageId: Long)
    fun streamAssistantReply(settings: AppSettings, history: List<MessageEntity>): Flow<String>
    suspend fun fetchAvailableModels(settings: AppSettings): Result<List<String>>
    suspend fun testConnection(settings: AppSettings): Result<String>

    suspend fun updateEndpointSettings(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int
    )

    suspend fun updateSystemPrompt(systemPrompt: String)
    suspend fun updateTheme(themeMode: ThemeMode, useDynamicColor: Boolean)
}

class DefaultChatRepository(
    private val chatDao: ChatDao,
    private val settingsRepository: SettingsRepository,
    private val streamClient: LlmStreamClient,
    private val memoryDao: MemoryDao? = null,
    private val localGenerationAdapter: LocalGenerationFlowAdapter? = null
) : ChatRepository {

    override val settingsFlow: Flow<AppSettings> = settingsRepository.settingsFlow

    override val conversationsFlow: Flow<List<ConversationWithPreview>> =
        chatDao.observeConversations()

    override val totalMessageCountFlow: Flow<Int> = chatDao.observeTotalMessageCount()

    override fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        chatDao.observeMessages(conversationId)

    override suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity> =
        chatDao.getMessagesOnce(conversationId)

    override suspend fun getConversation(conversationId: Long): ConversationEntity? =
        chatDao.getConversation(conversationId)

    override suspend fun createConversation(title: String, modelName: String): Long {
        val now = System.currentTimeMillis()
        return chatDao.insertConversation(
            ConversationEntity(
                title = title.trim().ifBlank { "New Conversation" },
                modelName = modelName.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun renameConversation(conversationId: Long, newTitle: String) {
        val cleaned = newTitle.trim()
        if (cleaned.isNotEmpty()) {
            chatDao.updateConversationTitle(
                conversationId = conversationId,
                title = cleaned.take(80),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        chatDao.deleteConversation(conversationId)
    }

    override suspend fun deleteAllConversations() {
        chatDao.deleteAllConversations()
    }

    override suspend fun saveMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        modelName: String,
        isError: Boolean
    ): Long {
        val now = System.currentTimeMillis()
        val id = chatDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role.wireValue,
                content = content,
                modelName = modelName,
                isError = isError,
                createdAt = now
            )
        )
        chatDao.touchConversation(
            conversationId = conversationId,
            modelName = modelName,
            updatedAt = now
        )
        return id
    }

    override suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessage(messageId)
    }

    override fun streamAssistantReply(
        settings: AppSettings,
        history: List<MessageEntity>
    ): Flow<String> = flow {
        val recentHistory = history.filterNot { it.isError }.takeLast(40)
        val latestUserText = recentHistory.lastOrNull { it.role == MessageRole.USER.wireValue }
            ?.content.orEmpty()
        val retrieved = memoryDao?.getActiveMemories()
            ?.let { memories ->
                val queryTerms = latestUserText.lowercase()
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.length >= 3 }
                    .toSet()
                memories.map { memory ->
                    val searchable = "${memory.subject} ${memory.content} ${memory.category}".lowercase()
                    val score = queryTerms.count { term -> searchable.contains(term) }
                    memory to score
                }
                    .filter { (memory, score) -> score > 0 || memory.isPinned }
                    .sortedWith(
                        compareByDescending<Pair<com.forgeai.chat.data.local.entity.MemoryEntity, Int>> { it.first.isPinned }
                            .thenByDescending { it.second }
                            .thenByDescending { it.first.updatedAt }
                    )
                    .take(8)
                    .map { it.first }
            }.orEmpty()

        val retrievedContext = retrieved.map { memory ->
            RetrievedMemory(
                memoryId = memory.memoryId,
                category = memory.category,
                subject = memory.subject,
                content = memory.content,
                status = RetrievedMemory.Status.CONFIRMED
            )
        }
        val system = PersonaOrchestrator.assemble(
            persona = PersonaSpecification(
                revision = "settings-system-prompt-v1",
                canonicalPrompt = settings.systemPrompt
            ),
            memories = retrievedContext
        )

        val adapter = localGenerationAdapter
            ?: throw IllegalStateException("Local generation adapter is not configured")
        if (!adapter.isReadyForChat()) {
            throw IllegalStateException("Local model is not READY_FOR_CHAT")
        }

        val turns = recentHistory
            .filterNot { it.role == MessageRole.SYSTEM.wireValue }
            .map { message ->
                Llama3PromptBuilder.ChatTurn(
                    role = if (message.role == MessageRole.ASSISTANT.wireValue) "assistant" else "user",
                    content = message.content
                )
            }

        if (retrieved.isNotEmpty()) {
            memoryDao?.markAccessed(retrieved.map { it.memoryId })
        }

        emitAll(
            adapter.generateChat(
                systemPrompt = system,
                messages = turns,
                temperature = settings.temperature,
                maxTokens = settings.maxTokens
            )
        )
    }

    override suspend fun fetchAvailableModels(settings: AppSettings): Result<List<String>> =
        streamClient.fetchAvailableModels(settings)

    override suspend fun testConnection(settings: AppSettings): Result<String> =
        streamClient.testConnection(settings)

    override suspend fun updateEndpointSettings(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int
    ) {
        settingsRepository.updateEndpointSettings(
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    override suspend fun updateSystemPrompt(systemPrompt: String) {
        settingsRepository.updateSystemPrompt(systemPrompt)
    }

    override suspend fun updateTheme(themeMode: ThemeMode, useDynamicColor: Boolean) {
        settingsRepository.updateTheme(themeMode, useDynamicColor)
    }
}
