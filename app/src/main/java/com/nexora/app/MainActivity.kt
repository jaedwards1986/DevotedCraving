package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    HordeChatScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HordeChatScreen() {
    val client = remember { AiHordeClient() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val models = remember { mutableStateListOf<HordeModel>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("0000000000") }
    var selectedModel by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Connect to AI Horde to load available models.") }
    var loadingModels by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        loadingModels = true
        runCatching { client.getTextModels(apiKey) }
            .onSuccess {
                models.clear()
                models.addAll(it)
                status = "Found ${it.size} active text models."
            }
            .onFailure { status = it.message ?: "Could not load models." }
        loadingModels = false
    }

    BackHandler(enabled = generationJob?.isActive == true) {
        generationJob?.cancel()
        generationJob = null
        status = "Generation cancelled."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NEXORA") },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                loadingModels = true
                                runCatching { client.getTextModels(apiKey) }
                                    .onSuccess {
                                        models.clear()
                                        models.addAll(it)
                                        status = "Found ${it.size} active text models."
                                    }
                                    .onFailure { status = it.message ?: "Could not load models." }
                                loadingModels = false
                            }
                        }
                    ) { Text(if (loadingModels) "Loading" else "Refresh models") }
                }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Message") },
                    enabled = generationJob?.isActive != true,
                    maxLines = 4
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty() && generationJob?.isActive != true) {
                                input = ""
                                messages.add(ChatMessage(text, true))
                                val history = messages.toList()
                                generationJob = scope.launch {
                                    status = "Submitting request to AI Horde…"
                                    try {
                                        val prompt = buildString {
                                            append("The following is a conversation between a user and an AI assistant. Continue as the assistant.\n\n")
                                            history.takeLast(16).forEach { message ->
                                                append(if (message.fromUser) "User: " else "Assistant: ")
                                                append(message.text)
                                                append("\n")
                                            }
                                            append("Assistant:")
                                        }
                                        val reply = client.generate(
                                            prompt = prompt,
                                            apiKey = apiKey,
                                            model = selectedModel.ifBlank { null }
                                        )
                                        messages.add(ChatMessage(reply.ifBlank { "(The model returned an empty response.)" }, false))
                                        status = "Response received."
                                    } catch (cancelled: CancellationException) {
                                        status = "Generation cancelled."
                                    } catch (error: Exception) {
                                        messages.add(ChatMessage(error.message ?: "Generation failed.", false))
                                        status = "Generation failed."
                                    } finally {
                                        generationJob = null
                                    }
                                }
                            }
                        },
                        enabled = input.isNotBlank() && generationJob?.isActive != true
                    ) { Text("Send") }

                    Spacer(Modifier.width(8.dp))
                    if (generationJob?.isActive == true) {
                        Button(onClick = { generationJob?.cancel() }) { Text("Stop") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { modelMenuOpen = true }) {
                        Text(if (selectedModel.isBlank()) "Any available model" else selectedModel)
                    }
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any available model") },
                            onClick = {
                                selectedModel = ""
                                modelMenuOpen = false
                            }
                        )
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text("${model.name} · ${model.workers} workers") },
                                onClick = {
                                    selectedModel = model.name
                                    modelMenuOpen = false
                                }
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        messages.clear()
                        status = "Conversation cleared."
                    }) { Text("Clear") }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI Horde API key (0000000000 for anonymous)") },
                    singleLine = true
                )
                Text(status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(message.text, Modifier.padding(12.dp))
                    }
                }
            }
            if (generationJob?.isActive == true) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("  Waiting for a volunteer worker…")
                    }
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
}
