package com.forgeai.chat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.forgeai.chat.data.local.entity.MessageRole
import com.forgeai.chat.data.preferences.AppSettings
import com.forgeai.chat.data.preferences.ThemeMode
import com.forgeai.chat.data.remote.EndpointValidator
import com.forgeai.chat.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _destination = MutableStateFlow(AppDestination.CHAT)
    private val _activeConversationId = MutableStateFlow<Long?>(null)
    private val _drawerSearchQuery = MutableStateFlow("")
    private val _inputText = MutableStateFlow("")
    private val _isStreaming = MutableStateFlow(false)
    private val _streamingContent = MutableStateFlow("")
    private val _streamingConversationId = MutableStateFlow<Long?>(null)
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    private val _isFetchingModels = MutableStateFlow(false)
    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    private var activeStreamJob: Job? = null
    private var latestSettings: AppSettings = AppSettings()

    private val activeMessagesFlow = _activeConversationId.flatMapLatest { conversationId ->
        if (conversationId == null) {
            flowOf(emptyList())
        } else {
            repository.observeMessages(conversationId)
        }
    }

    private data class CoreSnapshot(
        val settings: AppSettings,
        val conversations: List<com.forgeai.chat.data.local.entity.ConversationWithPreview>,
        val activeId: Long?,
        val messages: List<com.forgeai.chat.data.local.entity.MessageEntity>,
        val totalMessages: Int
    )

    private data class StreamingSnapshot(
        val isStreaming: Boolean,
        val streamingContent: String,
        val streamingConversationId: Long?,
        val inputText: String,
        val drawerSearchQuery: String
    )

    private data class MetaSnapshot(
        val destination: AppDestination,
        val availableModels: List<String>,
        val isFetchingModels: Boolean,
        val connectionTestState: ConnectionTestState,
        val snackbarMessage: String?
    )

    private val coreFlow = combine(
        repository.settingsFlow,
        repository.conversationsFlow,
        _activeConversationId,
        activeMessagesFlow,
        repository.totalMessageCountFlow
    ) { settings, conversations, activeId, messages, totalMessages ->
        latestSettings = settings
        CoreSnapshot(settings, conversations, activeId, messages, totalMessages)
    }

    private val streamingFlow = combine(
        _isStreaming,
        _streamingContent,
        _streamingConversationId,
        _inputText,
        _drawerSearchQuery
    ) { isStreaming, streamingContent, streamingConvId, inputText, searchQuery ->
        StreamingSnapshot(isStreaming, streamingContent, streamingConvId, inputText, searchQuery)
    }

    private val metaFlow = combine(
        _destination,
        _availableModels,
        _isFetchingModels,
        _connectionTestState,
        _snackbarMessage
    ) { destination, availableModels, isFetchingModels, connectionState, snackbar ->
        MetaSnapshot(destination, availableModels, isFetchingModels, connectionState, snackbar)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        coreFlow,
        streamingFlow,
        metaFlow
    ) { core, stream, meta ->
        val activeConv = core.conversations.firstOrNull { it.id == core.activeId }
        ChatUiState(
            destination = meta.destination,
            settings = core.settings,
            conversations = core.conversations,
            drawerSearchQuery = stream.drawerSearchQuery,
            activeConversationId = core.activeId,
            activeConversationTitle = activeConv?.title ?: "ForgeAI",
            messages = core.messages,
            inputText = stream.inputText,
            isStreaming = stream.isStreaming,
            streamingContent = stream.streamingContent,
            streamingConversationId = stream.streamingConversationId,
            totalMessageCount = core.totalMessages,
            availableModels = meta.availableModels,
            isFetchingModels = meta.isFetchingModels,
            connectionTestState = meta.connectionTestState,
            snackbarMessage = meta.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ChatUiState()
    )

    fun navigateTo(destination: AppDestination) {
        _destination.value = destination
        if (destination != AppDestination.SETTINGS) {
            _connectionTestState.value = ConnectionTestState.Idle
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onDrawerSearchChanged(query: String) {
        _drawerSearchQuery.value = query
    }

    fun startNewChat() {
        stopStreaming(persistPartial = true)
        _activeConversationId.value = null
        _inputText.value = ""
        _destination.value = AppDestination.CHAT
    }

    fun selectConversation(conversationId: Long) {
        _activeConversationId.value = conversationId
        _destination.value = AppDestination.CHAT
    }

    fun renameConversation(conversationId: Long, newTitle: String) {
        val cleaned = newTitle.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            repository.renameConversation(conversationId, cleaned)
            showSnackbar("Conversation renamed")
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            if (_streamingConversationId.value == conversationId) {
                stopStreaming(persistPartial = false)
            }
            repository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
            showSnackbar("Conversation deleted")
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            stopStreaming(persistPartial = false)
            repository.deleteAllConversations()
            _activeConversationId.value = null
            showSnackbar("All conversations cleared")
        }
    }

    fun sendSuggestion(prompt: String) {
        _inputText.value = prompt
        sendMessage()
    }

    fun sendMessage() {
        val prompt = _inputText.value.trim()
        if (prompt.isEmpty() || _isStreaming.value) return

        val settings = latestSettings
        val urlError = EndpointValidator.validateEndpointUrl(settings.baseUrl)
        if (urlError != null) {
            showSnackbar(urlError)
            return
        }
        val modelError = EndpointValidator.validateModelName(settings.modelName)
        if (modelError != null) {
            showSnackbar(modelError)
            return
        }

        _inputText.value = ""
        launchStreamForPrompt(prompt = prompt, settings = settings)
    }

    fun regenerateLastResponse() {
        if (_isStreaming.value) return
        val conversationId = _activeConversationId.value ?: return
        val currentMessages = uiState.value.messages
        if (currentMessages.isEmpty()) return

        viewModelScope.launch {
            val last = currentMessages.last()
            if (MessageRole.fromWire(last.role) == MessageRole.ASSISTANT) {
                repository.deleteMessage(last.id)
            }
            val updatedHistory = repository.getMessagesOnce(conversationId)
            if (updatedHistory.none { MessageRole.fromWire(it.role) == MessageRole.USER }) {
                return@launch
            }
            streamAssistantForConversation(
                conversationId = conversationId,
                settings = latestSettings
            )
        }
    }

    fun stopStreaming(persistPartial: Boolean = true) {
        val currentJob = activeStreamJob ?: return
        val partialText = _streamingContent.value.trim()
        val targetConvId = _streamingConversationId.value
        val modelUsed = latestSettings.modelName

        activeStreamJob = null
        currentJob.cancel()
        _isStreaming.value = false
        _streamingContent.value = ""
        _streamingConversationId.value = null

        if (persistPartial && targetConvId != null && partialText.isNotEmpty()) {
            viewModelScope.launch {
                repository.saveMessage(
                    conversationId = targetConvId,
                    role = MessageRole.ASSISTANT,
                    content = partialText,
                    modelName = modelUsed,
                    isError = false
                )
            }
        }
    }

    fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            repository.updateSystemPrompt(prompt)
            showSnackbar("System prompt saved")
        }
    }

    fun saveEndpointSettings(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int
    ): Boolean {
        val urlError = EndpointValidator.validateEndpointUrl(baseUrl)
        if (urlError != null) {
            showSnackbar(urlError)
            return false
        }
        val modelError = EndpointValidator.validateModelName(modelName)
        if (modelError != null) {
            showSnackbar(modelError)
            return false
        }
        viewModelScope.launch {
            repository.updateEndpointSettings(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName,
                temperature = temperature,
                maxTokens = maxTokens
            )
            showSnackbar("Endpoint & model settings saved")
        }
        return true
    }

    fun selectQuickModel(modelName: String) {
        val cleaned = modelName.trim()
        if (cleaned.isEmpty()) return
        val current = latestSettings
        viewModelScope.launch {
            repository.updateEndpointSettings(
                baseUrl = current.baseUrl,
                apiKey = current.apiKey,
                modelName = cleaned,
                temperature = current.temperature,
                maxTokens = current.maxTokens
            )
            showSnackbar("Switched model to $cleaned")
        }
    }

    fun updateTheme(themeMode: ThemeMode, useDynamicColor: Boolean) {
        viewModelScope.launch {
            repository.updateTheme(themeMode, useDynamicColor)
        }
    }

    fun fetchRemoteModels(baseUrl: String, apiKey: String) {
        if (_isFetchingModels.value) return
        val probeSettings = latestSettings.copy(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim()
        )
        viewModelScope.launch {
            _isFetchingModels.value = true
            val result = repository.fetchAvailableModels(probeSettings)
            _isFetchingModels.value = false
            result.fold(
                onSuccess = { models ->
                    _availableModels.value = models
                    showSnackbar("Loaded ${models.size} models from endpoint")
                },
                onFailure = { error ->
                    showSnackbar(error.message ?: "Failed to load models")
                }
            )
        }
    }

    fun testConnection(baseUrl: String, apiKey: String, modelName: String) {
        val probeSettings = latestSettings.copy(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            modelName = modelName.trim()
        )
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            val result = repository.testConnection(probeSettings)
            _connectionTestState.value = result.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Failure(it.message ?: "Connection failed") }
            )
        }
    }

    fun notifyCopied(label: String = "Copied to clipboard") {
        showSnackbar(label)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    private fun launchStreamForPrompt(prompt: String, settings: AppSettings) {
        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            val conversationId = _activeConversationId.value ?: run {
                val title = buildConversationTitle(prompt)
                val newId = repository.createConversation(title = title, modelName = settings.modelName)
                _activeConversationId.value = newId
                newId
            }

            repository.saveMessage(
                conversationId = conversationId,
                role = MessageRole.USER,
                content = prompt,
                modelName = settings.modelName
            )

            executeStream(conversationId = conversationId, settings = settings)
        }
    }

    private fun streamAssistantForConversation(conversationId: Long, settings: AppSettings) {
        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            executeStream(conversationId = conversationId, settings = settings)
        }
    }

    private suspend fun executeStream(conversationId: Long, settings: AppSettings) {
        _isStreaming.value = true
        _streamingConversationId.value = conversationId
        _streamingContent.value = ""

        val buffer = StringBuilder()
        try {
            val history = repository.getMessagesOnce(conversationId)
            repository.streamAssistantReply(settings = settings, history = history)
                .collect { token ->
                    buffer.append(token)
                    _streamingContent.value = buffer.toString()
                }

            val finalReply = buffer.toString().trim()
            if (finalReply.isNotEmpty()) {
                repository.saveMessage(
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = finalReply,
                    modelName = settings.modelName,
                    isError = false
                )
            } else {
                repository.saveMessage(
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = "The model returned an empty response. Check your model ID and endpoint settings.",
                    modelName = settings.modelName,
                    isError = true
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val partial = buffer.toString().trim()
            val errorMessage = failure.message?.takeIf { it.isNotBlank() }
                ?: "Request failed. Verify your endpoint URL, API key, and network connection."
            val contentToPersist = if (partial.isNotEmpty()) {
                "$partial\n\n---\n*Stream interrupted: $errorMessage*"
            } else {
                errorMessage
            }
            repository.saveMessage(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = contentToPersist,
                modelName = settings.modelName,
                isError = partial.isEmpty()
            )
        } finally {
            _isStreaming.value = false
            _streamingContent.value = ""
            _streamingConversationId.value = null
            activeStreamJob = null
        }
    }

    override fun onCleared() {
        activeStreamJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun buildConversationTitle(firstPrompt: String): String {
            val singleLine = firstPrompt
                .replace(Regex("\\s+"), " ")
                .trim()
            return if (singleLine.length <= 44) {
                singleLine.ifBlank { "New Conversation" }
            } else {
                singleLine.take(44).trimEnd() + "…"
            }
        }

        fun factory(repository: ChatRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
