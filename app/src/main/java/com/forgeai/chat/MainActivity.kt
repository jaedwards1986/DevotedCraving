package com.forgeai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forgeai.chat.ui.components.ConversationDrawerContent
import com.forgeai.chat.ui.screens.ChatScreen
import com.forgeai.chat.ui.screens.SettingsScreen
import com.forgeai.chat.ui.screens.SplashScreen
import com.forgeai.chat.ui.screens.SystemPromptScreen
import com.forgeai.chat.ui.theme.ForgeAiTheme
import com.forgeai.chat.ui.viewmodel.AppDestination
import com.forgeai.chat.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        val app = application as ForgeAiApplication
        ChatViewModel.factory(app.container.chatRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            ForgeAiTheme(
                themeMode = uiState.settings.themeMode,
                useDynamicColor = uiState.settings.useDynamicColor
            ) {
                ForgeAiAppRoot(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}

@Composable
private fun ForgeAiAppRoot(
    viewModel: ChatViewModel,
    uiState: com.forgeai.chat.ui.viewmodel.ChatUiState
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
        viewModel.clearSnackbar()
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && uiState.destination != AppDestination.CHAT) {
        viewModel.navigateTo(AppDestination.CHAT)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = uiState.destination == AppDestination.CHAT,
            drawerContent = {
                ConversationDrawerContent(
                    conversations = uiState.filteredConversations,
                    activeConversationId = uiState.activeConversationId,
                    searchQuery = uiState.drawerSearchQuery,
                    activeModelName = uiState.settings.modelName,
                    onSearchQueryChange = viewModel::onDrawerSearchChanged,
                    onNewChat = {
                        viewModel.startNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onRenameConversation = viewModel::renameConversation,
                    onDeleteConversation = viewModel::deleteConversation,
                    onOpenSystemPrompt = {
                        viewModel.navigateTo(AppDestination.SYSTEM_PROMPT)
                        scope.launch { drawerState.close() }
                    },
                    onOpenSettings = {
                        viewModel.navigateTo(AppDestination.SETTINGS)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            AnimatedContent(
                targetState = uiState.destination,
                transitionSpec = {
                    if (targetState == AppDestination.CHAT) {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    }
                },
                label = "destination_transition"
            ) { destination ->
                when (destination) {
                    AppDestination.CHAT -> {
                        ChatScreen(
                            uiState = uiState,
                            snackbarHostState = snackbarHostState,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNewChat = viewModel::startNewChat,
                            onOpenSystemPrompt = { viewModel.navigateTo(AppDestination.SYSTEM_PROMPT) },
                            onOpenSettings = { viewModel.navigateTo(AppDestination.SETTINGS) },
                            onSelectModel = viewModel::selectQuickModel,
                            onInputChanged = viewModel::onInputTextChanged,
                            onSendMessage = viewModel::sendMessage,
                            onStopStreaming = { viewModel.stopStreaming(persistPartial = true) },
                            onSendSuggestion = viewModel::sendSuggestion,
                            onRegenerate = viewModel::regenerateLastResponse,
                            onCopiedFeedback = viewModel::notifyCopied
                        )
                    }

                    AppDestination.SYSTEM_PROMPT -> {
                        SystemPromptScreen(
                            currentPrompt = uiState.settings.systemPrompt,
                            snackbarHostState = snackbarHostState,
                            onNavigateBack = { viewModel.navigateTo(AppDestination.CHAT) },
                            onSavePrompt = viewModel::saveSystemPrompt
                        )
                    }

                    AppDestination.SETTINGS -> {
                        SettingsScreen(
                            uiState = uiState,
                            snackbarHostState = snackbarHostState,
                            onNavigateBack = { viewModel.navigateTo(AppDestination.CHAT) },
                            onSaveEndpointSettings = viewModel::saveEndpointSettings,
                            onFetchModels = viewModel::fetchRemoteModels,
                            onTestConnection = viewModel::testConnection,
                            onUpdateTheme = viewModel::updateTheme,
                            onClearAllConversations = viewModel::clearAllConversations
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut()
        ) {
            SplashScreen(onFinished = { showSplash = false })
        }
    }
}
