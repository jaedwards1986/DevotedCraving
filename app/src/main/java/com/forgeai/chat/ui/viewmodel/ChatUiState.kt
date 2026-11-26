package com.forgeai.chat.ui.viewmodel

import com.forgeai.chat.data.local.entity.ConversationWithPreview
import com.forgeai.chat.data.local.entity.MessageEntity
import com.forgeai.chat.data.preferences.AppSettings

enum class AppDestination {
    CHAT,
    SYSTEM_PROMPT,
    SETTINGS,
    MEMORY
}

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState
    data class Failure(val message: String) : ConnectionTestState
}

data class PromptSuggestion(
    val title: String,
    val subtitle: String,
    val prompt: String
)

data class ChatUiState(
    val destination: AppDestination = AppDestination.CHAT,
    val settings: AppSettings = AppSettings(),
    val conversations: List<ConversationWithPreview> = emptyList(),
    val drawerSearchQuery: String = "",
    val activeConversationId: Long? = null,
    val activeConversationTitle: String = "New conversation",
    val messages: List<MessageEntity> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val streamingConversationId: Long? = null,
    val totalMessageCount: Int = 0,
    val availableModels: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
    val snackbarMessage: String? = null
) {
    val filteredConversations: List<ConversationWithPreview>
        get() {
            val query = drawerSearchQuery.trim()
            if (query.isEmpty()) return conversations
            return conversations.filter { conv ->
                conv.title.contains(query, ignoreCase = true) ||
                    (conv.lastMessage?.contains(query, ignoreCase = true) == true)
            }
        }

    val isActiveConversationStreaming: Boolean
        get() = isStreaming && streamingConversationId == activeConversationId

    val hasApiKeyConfigured: Boolean
        get() = settings.apiKey.isNotBlank() ||
            settings.baseUrl.contains("localhost", ignoreCase = true) ||
            settings.baseUrl.contains("127.0.0.1") ||
            settings.baseUrl.contains("10.0.2.2")

    companion object {
        val DEFAULT_SUGGESTIONS: List<PromptSuggestion> = listOf(
            PromptSuggestion(
                title = "Jetpack Compose",
                subtitle = "Build an animated M3 bottom sheet",
                prompt = "Write a production-ready Jetpack Compose Material 3 ModalBottomSheet with smooth animations and StateFlow integration in Kotlin."
            ),
            PromptSuggestion(
                title = "Debug & Optimize",
                subtitle = "Audit Kotlin coroutine cancellation",
                prompt = "Explain common structured concurrency pitfalls in Android ViewModels and show a clean Kotlin pattern for cancelling streaming network calls."
            ),
            PromptSuggestion(
                title = "System Design",
                subtitle = "Offline-first sync with Room",
                prompt = "Design a clean offline-first caching and sync architecture on Android using Room, Flow, and Retrofit with conflict resolution."
            ),
            PromptSuggestion(
                title = "SQL Performance",
                subtitle = "Optimize composite indexes",
                prompt = "Compare B-tree composite index column ordering for a chat messages table queried by conversation_id and created_at, with SQL examples."
            )
        )
    }
}
