package com.forgeai.chat.ui.viewmodel

import com.forgeai.chat.data.local.entity.ConversationEntity
import com.forgeai.chat.data.local.entity.ConversationWithPreview
import com.forgeai.chat.data.local.entity.MessageEntity
import com.forgeai.chat.data.local.entity.MessageRole
import com.forgeai.chat.data.preferences.AppSettings
import com.forgeai.chat.data.preferences.ThemeMode
import com.forgeai.chat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessageCreatesConversationStreamsTokensAndPersistsReply() = runTest {
        val fakeRepo = FakeChatRepository(tokensToEmit = listOf("Hello", ", ", "ForgeAI!"))
        val viewModel = ChatViewModel(fakeRepo)

        viewModel.onInputTextChanged("Explain Kotlin StateFlow")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isStreaming)
        assertNotNull(state.activeConversationId)
        assertEquals("Explain Kotlin StateFlow", state.activeConversationTitle)
        assertEquals(2, state.messages.size)
        assertEquals(MessageRole.USER.wireValue, state.messages[0].role)
        assertEquals(MessageRole.ASSISTANT.wireValue, state.messages[1].role)
        assertEquals("Hello, ForgeAI!", state.messages[1].content)
    }

    @Test
    fun streamingFailurePersistsVisibleErrorMessage() = runTest {
        val fakeRepo = FakeChatRepository(streamFailure = IOException("HTTP 401: Unauthorized"))
        val viewModel = ChatViewModel(fakeRepo)

        viewModel.sendSuggestion("Ping")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertTrue(state.messages.last().isError)
        assertTrue(state.messages.last().content.contains("401"))
    }

    @Test
    fun systemPromptAndEndpointSettingsUpdateStateFlow() = runTest {
        val fakeRepo = FakeChatRepository()
        val viewModel = ChatViewModel(fakeRepo)

        viewModel.saveSystemPrompt("You are a Kotlin expert.")
        val saved = viewModel.saveEndpointSettings(
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-test-123",
            modelName = "anthropic/claude-3.5-sonnet",
            temperature = 0.4f,
            maxTokens = 4096
        )
        advanceUntilIdle()

        assertTrue(saved)
        val settings = viewModel.uiState.value.settings
        assertEquals("You are a Kotlin expert.", settings.systemPrompt)
        assertEquals("https://openrouter.ai/api/v1", settings.baseUrl)
        assertEquals("anthropic/claude-3.5-sonnet", settings.modelName)
        assertEquals(4096, settings.maxTokens)
    }

    @Test
    fun renameAndDeleteConversationUpdatesDrawerState() = runTest {
        val fakeRepo = FakeChatRepository(tokensToEmit = listOf("OK"))
        val viewModel = ChatViewModel(fakeRepo)

        viewModel.sendSuggestion("First chat")
        advanceUntilIdle()
        val convId = requireNotNull(viewModel.uiState.value.activeConversationId)

        viewModel.renameConversation(convId, "Android Architecture")
        advanceUntilIdle()
        assertEquals("Android Architecture", viewModel.uiState.value.activeConversationTitle)

        viewModel.deleteConversation(convId)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.conversations.size)
        assertEquals(null, viewModel.uiState.value.activeConversationId)
    }

    private class FakeChatRepository(
        private val tokensToEmit: List<String> = emptyList(),
        private val streamFailure: Throwable? = null
    ) : ChatRepository {
        private var nextConvId = 1L
        private var nextMsgId = 1L

        private val settings = MutableStateFlow(AppSettings(apiKey = "sk-test"))
        private val conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
        private val messages = MutableStateFlow<List<MessageEntity>>(emptyList())

        override val settingsFlow: Flow<AppSettings> = settings

        override val conversationsFlow: Flow<List<ConversationWithPreview>> =
            conversations.map { list ->
                list.sortedByDescending { it.updatedAt }.map { conv ->
                    val convMsgs = messages.value.filter { it.conversationId == conv.id }
                    ConversationWithPreview(
                        id = conv.id,
                        title = conv.title,
                        modelName = conv.modelName,
                        createdAt = conv.createdAt,
                        updatedAt = conv.updatedAt,
                        lastMessage = convMsgs.lastOrNull()?.content,
                        messageCount = convMsgs.size
                    )
                }
            }

        override val totalMessageCountFlow: Flow<Int> = messages.map { it.size }

        override fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
            messages.map { list -> list.filter { it.conversationId == conversationId } }

        override suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity> =
            messages.value.filter { it.conversationId == conversationId }

        override suspend fun getConversation(conversationId: Long): ConversationEntity? =
            conversations.value.firstOrNull { it.id == conversationId }

        override suspend fun createConversation(title: String, modelName: String): Long {
            val id = nextConvId++
            conversations.value = conversations.value + ConversationEntity(
                id = id,
                title = title,
                modelName = modelName
            )
            return id
        }

        override suspend fun renameConversation(conversationId: Long, newTitle: String) {
            conversations.value = conversations.value.map {
                if (it.id == conversationId) it.copy(title = newTitle) else it
            }
        }

        override suspend fun deleteConversation(conversationId: Long) {
            conversations.value = conversations.value.filterNot { it.id == conversationId }
            messages.value = messages.value.filterNot { it.conversationId == conversationId }
        }

        override suspend fun deleteAllConversations() {
            conversations.value = emptyList()
            messages.value = emptyList()
        }

        override suspend fun saveMessage(
            conversationId: Long,
            role: MessageRole,
            content: String,
            modelName: String,
            isError: Boolean
        ): Long {
            val id = nextMsgId++
            messages.value = messages.value + MessageEntity(
                id = id,
                conversationId = conversationId,
                role = role.wireValue,
                content = content,
                modelName = modelName,
                isError = isError
            )
            return id
        }

        override suspend fun deleteMessage(messageId: Long) {
            messages.value = messages.value.filterNot { it.id == messageId }
        }

        override fun streamAssistantReply(
            settings: AppSettings,
            history: List<MessageEntity>
        ): Flow<String> = flow {
            streamFailure?.let { throw it }
            tokensToEmit.forEach { emit(it) }
        }

        override suspend fun fetchAvailableModels(settings: AppSettings): Result<List<String>> =
            Result.success(listOf("gpt-4o-mini", "gpt-4o"))

        override suspend fun testConnection(settings: AppSettings): Result<String> =
            Result.success("Connected")

        override suspend fun updateEndpointSettings(
            baseUrl: String,
            apiKey: String,
            modelName: String,
            temperature: Float,
            maxTokens: Int
        ) {
            settings.value = settings.value.copy(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName,
                temperature = temperature,
                maxTokens = maxTokens
            )
        }

        override suspend fun updateSystemPrompt(systemPrompt: String) {
            settings.value = settings.value.copy(systemPrompt = systemPrompt)
        }

        override suspend fun updateTheme(themeMode: ThemeMode, useDynamicColor: Boolean) {
            settings.value = settings.value.copy(
                themeMode = themeMode,
                useDynamicColor = useDynamicColor
            )
        }
    }
}
