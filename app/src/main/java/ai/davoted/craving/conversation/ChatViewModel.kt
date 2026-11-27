package ai.davoted.craving.conversation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ai.davoted.craving.ai.ArchiveContext

class ChatViewModel(
    private val service: ConversationService
) : ViewModel() {
    val state: StateFlow<ConversationUiState> = service.state

    init {
        viewModelScope.launch { service.initialize() }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch { service.importAndLoad(uri) }
    }

    fun setMode(mode: ConversationMode) {
        viewModelScope.launch { service.setMode(mode) }
    }

    fun selectArchiveContext(context: ArchiveContext?) = service.selectArchiveContext(context)

    fun sendPrompt(prompt: String) {
        viewModelScope.launch { service.sendPrompt(prompt) }
    }

    override fun onCleared() {
        service.close()
    }
}

class ChatViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(ConversationService(context.applicationContext)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
