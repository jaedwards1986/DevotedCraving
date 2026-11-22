package com.natasha.local.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ModelHubScreen(onDownloaded: (File) -> Unit) {
    DisposableEffect(Unit) {
        onDispose { HuggingFaceModelHub.cancelDownload() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("Qwen3 4B Instruct 2507 GGUF") }
    var models by remember { mutableStateOf(emptyList<HubModel>()) }
    var selectedModel by remember { mutableStateOf<HubModel?>(null) }
    var files by remember { mutableStateOf(emptyList<HubGgufFile>()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pendingDownload by remember { mutableStateOf<HubGgufFile?>(null) }
    var status by remember { mutableStateOf("Search public Hugging Face GGUF repositories. Gated repositories may require an account and are not downloadable anonymously.") }

    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text("Model catalog", style = MaterialTheme.typography.titleLarge)
        Text("Find and download public GGUF models without leaving ForgeAI.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search models") }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !busy && query.trim().length >= 2,
            onClick = {
                busy = true
                status = "Searching model catalog…"
                scope.launch {
                    try {
                        models = withContext(Dispatchers.IO) { HuggingFaceModelHub.search(query) }
                        selectedModel = null
                        files = emptyList()
                        status = if (models.isEmpty()) "No public GGUF repositories found." else "Choose a repository to inspect its GGUF files."
                    } catch (failure: Throwable) {
                        status = failure.message ?: "Catalog search failed"
                    } finally {
                        busy = false
                    }
                }
            }
        ) { Text("Search catalog") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy && progress > 0f) OutlinedButton(onClick = { HuggingFaceModelHub.cancelDownload() }) { Text("Cancel download") }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(models, key = { it.id }) { model ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        selectedModel = model
                        busy = true
                        status = "Loading GGUF files from ${model.id}…"
                        scope.launch {
                            try {
                                files = withContext(Dispatchers.IO) { HuggingFaceModelHub.ggufFiles(model.id) }
                                status = if (files.isEmpty()) "No GGUF files found in this repository." else "Select a quantization to download."
                            } catch (failure: Throwable) {
                                status = failure.message ?: "Unable to inspect repository"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(model.id + " · " + model.downloads + " downloads")
                }
            }
        }
        selectedModel?.let { selected ->
            Spacer(Modifier.height(8.dp))
            Text(selected.id, style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(files, key = { it.name }) { file ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (file.sizeBytes > 0) android.text.format.Formatter.formatShortFileSize(context, file.sizeBytes) else "Unknown size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                enabled = !busy && file.sizeBytes > 0,
                                onClick = { pendingDownload = file }
                            ) { Text("Download into ForgeAI") }
                        }
                    }
                }
            }
        }
        if (busy && progress > 0f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        pendingDownload?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingDownload = null },
                title = { Text("Review model download") },
                text = { Text("Download " + file.name + "? Check the model card and license at the source repository before use. ForgeAI does not grant rights to third-party model files.") },
                confirmButton = {
                    TextButton(onClick = {
                        val model = selectedModel ?: return@TextButton
                        pendingDownload = null
                        busy = true
                        progress = 0f
                        status = "Downloading " + file.name + "…"
                        scope.launch {
                            try {
                                val downloaded = withContext(Dispatchers.IO) {
                                    HuggingFaceModelHub.download(context, model.id, file) { done, total ->
                                        val value = if (total > 0) done.toFloat() / total.toFloat() else 0f
                                        Handler(Looper.getMainLooper()).post { progress = value }
                                    }
                                }
                                status = "Downloaded " + downloaded.name + ". Adding it to your models…"
                                onDownloaded(downloaded)
                            } catch (failure: Throwable) {
                                status = failure.message ?: "Model download failed"
                            } finally {
                                busy = false
                                progress = 0f
                            }
                        }
                    }) { Text("Download") }
                },
                dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text("Not now") } }
            )
        }
    }
}
