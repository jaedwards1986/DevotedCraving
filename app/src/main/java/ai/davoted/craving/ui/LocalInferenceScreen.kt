package ai.davoted.craving.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ai.davoted.craving.ai.ConversationMessage
import ai.davoted.craving.ai.ExperiencePresentationMapper
import ai.davoted.craving.ai.LlamaCppAdapter
import ai.davoted.craving.ai.LocalModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@Composable
fun LocalInferenceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adapter = remember { LlamaCppAdapter() }
    val messages = remember { mutableStateListOf<ConversationMessage>() }
    val presentationMapper = remember { ExperiencePresentationMapper() }
    var status by remember { mutableStateOf(if (adapter.status == LocalModelStatus.UNAVAILABLE) "Native runtime unavailable" else "Choose a GGUF model") }
    var modelName by remember { mutableStateOf("No model loaded") }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastPresentation by remember { mutableStateOf("No generated response yet") }

    DisposableEffect(adapter) {
        onDispose { adapter.close() }
    }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "Importing model into private app storage…"
                val imported = runCatching { importGguf(context, uri) }
                imported.fold(
                    onSuccess = { file ->
                        modelName = file.name
                        status = "Loading GGUF on device…"
                        val loaded = adapter.loadModel(file.absolutePath)
                        loaded.fold(
                            onSuccess = { status = "Model ready — local inference enabled" },
                            onFailure = { status = "Model load failed: ${it.message ?: "unknown error"}" }
                        )
                    },
                    onFailure = { status = "Model import failed: ${it.message ?: "unknown error"}" }
                )
                busy = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("LOCAL EXPERIENCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("On-device model", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(modelName, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { modelPicker.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import / select GGUF model") }
            }
        }

        Text("Conversation", style = MaterialTheme.typography.titleLarge)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item { Text("Import a compatible GGUF model to begin local generation. No remote inference fallback is used.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(messages) { message ->
                Card(colors = CardDefaults.cardColors(containerColor = if (message.role == "assistant") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(message.role.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(message.content)
                    }
                }
            }
        }

        Text(lastPresentation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && adapter.status == LocalModelStatus.READY,
            placeholder = { Text("Write a message…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (input.isNotBlank() && !busy) {
                    val prompt = input.trim()
                    input = ""
                    scope.launch {
                        busy = true
                        messages.add(ConversationMessage("user", prompt))
                        val result = adapter.generate(messages.toList())
                        result.fold(
                            onSuccess = { turn ->
                                messages.add(ConversationMessage("assistant", turn.assistantText))
                                val presentation = presentationMapper.map(turn.experienceState)
                                lastPresentation = "Validated state: ${turn.experienceState.mood} · intensity ${turn.experienceState.intensity} · ${turn.experienceState.phase} | Visual: ${presentation.visual} · Audio cue: ${presentation.audio} · Haptic cue: ${presentation.haptics}"
                                status = "Model ready — local inference enabled"
                            },
                            onFailure = { status = "Generation failed: ${it.message ?: "unknown error"}" }
                        )
                        busy = false
                    }
                }
            })
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    val prompt = input.trim()
                    if (prompt.isNotEmpty() && !busy && adapter.status == LocalModelStatus.READY) {
                        input = ""
                        scope.launch {
                            busy = true
                            messages.add(ConversationMessage("user", prompt))
                            adapter.generate(messages.toList()).fold(
                                onSuccess = { turn ->
                                    messages.add(ConversationMessage("assistant", turn.assistantText))
                                    val presentation = presentationMapper.map(turn.experienceState)
                                    lastPresentation = "Validated state: ${turn.experienceState.mood} · intensity ${turn.experienceState.intensity} · ${turn.experienceState.phase} | Visual: ${presentation.visual} · Audio cue: ${presentation.audio} · Haptic cue: ${presentation.haptics}"
                                    status = "Model ready — local inference enabled"
                                },
                                onFailure = { status = "Generation failed: ${it.message ?: "unknown error"}" }
                            )
                            busy = false
                        }
                    }
                },
                enabled = !busy && adapter.status == LocalModelStatus.READY && input.isNotBlank()
            ) { Text(if (busy) "Working…" else "Generate locally") }
        }
    }
}

private suspend fun importGguf(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val directory = File(context.filesDir, "models")
    check(directory.exists() || directory.mkdirs()) { "Could not create private model directory." }
    val destination = File.createTempFile("model-", ".gguf", directory)
    try {
        val source = context.contentResolver.openInputStream(uri)
            ?: error("The selected model could not be opened.")
        source.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        check(destination.length() >= 4L) { "The selected file is empty or truncated." }
        val magic = ByteArray(4)
        FileInputStream(destination).use { stream ->
            check(stream.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                "The selected file is not a GGUF model."
            }
        }
        destination
    } catch (failure: Throwable) {
        destination.delete()
        throw failure
    }
}
