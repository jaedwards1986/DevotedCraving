package com.nexora.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.nexora.app.model.LocalModel
import com.nexora.app.model.LocalModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private data class ChatLine(val speaker: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { NexoraScreen() } }
    }
}

@Composable
private fun NexoraScreen() {
    val context = LocalContext.current
    val store = remember { LocalModelStore(context) }
    val engine = remember { AiChat.getInferenceEngine(context) }
    val engineState by engine.state.collectAsState()
    val scope = rememberCoroutineScope()

    var models by remember { mutableStateOf<List<LocalModel>>(emptyList()) }
    var selected by remember { mutableStateOf(store.selectedModel()) }
    var status by remember { mutableStateOf("Import a GGUF model to begin.") }
    var systemPrompt by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(emptyList<ChatLine>()) }
    var chatMode by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            models = store.list()
            selected = store.selectedModel()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = "Importing model…"
            try {
                val model = store.import(uri, queryDisplayName(context, uri))
                refresh()
                status = "Imported ${model.fileName}."
            } catch (error: Exception) {
                status = error.message ?: "Model import failed."
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun loadModel(fileName: String) {
        scope.launch {
            status = "Starting local inference runtime…"
            try {
                val ready = withTimeout(180_000L) {
                    engine.state.first {
                        it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
                    }
                }
                if (ready is InferenceEngine.State.Error) {
                    throw ready.exception
                }
                if (engine.state.value is InferenceEngine.State.ModelReady) {
                    withContext(Dispatchers.IO) { engine.cleanUp() }
                }
                status = "Loading $fileName…"
                engine.loadModel(store.modelFile(fileName).absolutePath)
                if (systemPrompt.isNotBlank()) engine.setSystemPrompt(systemPrompt)
                selected = fileName
                chatMode = true
                status = "Model loaded locally. No cloud inference is used."
            } catch (error: Exception) {
                status = error.message ?: "Could not load model."
            }
        }
    }

    fun sendMessage() {
        val message = draft.trim()
        if (message.isEmpty() || generating) return
        draft = ""
        messages = messages + ChatLine("You", message) + ChatLine("NEXORA", "")
        generating = true
        scope.launch {
            try {
                engine.sendUserPrompt(message).collect { token ->
                    val last = messages.lastOrNull()
                    if (last?.speaker == "NEXORA") {
                        messages = messages.dropLast(1) + last.copy(text = last.text + token)
                    }
                }
            } catch (error: Exception) {
                status = error.message ?: "Generation failed."
            } finally {
                generating = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (chatMode) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NEXORA", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { chatMode = false }) { Text("Models") }
                }
                Text(selected ?: "Local model", style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { line ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(line.speaker, fontWeight = FontWeight.SemiBold)
                                Text(line.text)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message your local model") },
                        enabled = !generating
                    )
                    Button(onClick = { sendMessage() }, enabled = !generating && draft.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("NEXORA", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Local model library", style = MaterialTheme.typography.titleLarge)
                Text(
                    "GGUF models are stored privately on this device. Chat generation runs through the bundled llama.cpp runtime.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional system prompt (visible and editable)") },
                    minLines = 2
                )
                Button(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import GGUF model") }
                Text(status, style = MaterialTheme.typography.bodySmall)
                if (models.isEmpty()) {
                    Text("No models imported yet.", modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(models, key = { it.fileName }) { model ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(model.fileName, fontWeight = FontWeight.SemiBold)
                                    Text(formatBytes(model.sizeBytes), style = MaterialTheme.typography.bodySmall)
                                    if (selected == model.fileName) {
                                        Text("Selected model", color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { loadModel(model.fileName) }) {
                                            Text("Load & Chat")
                                        }
                                        OutlinedButton(onClick = {
                                            scope.launch {
                                                try {
                                                    store.delete(model.fileName)
                                                    refresh()
                                                    status = "Deleted ${model.fileName}."
                                                } catch (error: Exception) {
                                                    status = error.message ?: "Could not delete model."
                                                }
                                            }
                                        }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Initial runtime integration. Device performance and model compatibility still require Pixel testing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )
    return cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) String.format("%.2f GB", gib)
    else String.format("%.0f MB", bytes / (1024.0 * 1024.0))
}
