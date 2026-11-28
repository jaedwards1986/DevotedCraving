package com.nexora.app

import android.content.Intent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nexora.app.model.LocalModel
import com.nexora.app.model.LocalModelStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NexoraModelScreen()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NexoraModelScreen() {
    val context = LocalContext.current
    val store = remember { LocalModelStore(context) }
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<LocalModel>>(emptyList()) }
    var selected by remember { mutableStateOf(store.selectedModel()) }
    var status by remember { mutableStateOf("Import a GGUF model to begin.") }

    fun refresh() {
        scope.launch {
            models = store.list()
            selected = store.selectedModel()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                status = "Importing model…"
                try {
                    val name = queryDisplayName(context, uri)
                    val model = store.import(uri, name)
                    refresh()
                    status = "Imported ${model.fileName}."
                } catch (error: Exception) {
                    status = error.message ?: "Model import failed."
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("NEXORA", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Local model library", style = MaterialTheme.typography.titleLarge)
            Text(
                "Models are copied into app-private storage. Chat inference is not connected yet.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import GGUF model")
            }
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(onClick = {
                                        try {
                                            store.select(model.fileName)
                                            selected = model.fileName
                                            status = "Selected ${model.fileName}. Inference is not available yet."
                                        } catch (error: Exception) {
                                            status = error.message ?: "Could not select model."
                                        }
                                    }) { Text("Select") }
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Next: connect the native llama.cpp runtime to the selected model.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) String.format("%.2f GB", gib)
    else String.format("%.0f MB", bytes / (1024.0 * 1024.0))
}
