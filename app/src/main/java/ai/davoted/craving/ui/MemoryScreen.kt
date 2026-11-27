package ai.davoted.craving.ui

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.davoted.craving.storage.LocalContinuityStore
import kotlinx.coroutines.launch

@Composable
fun MemoryScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalContinuityStore(context) }
    val preferences = remember { mutableStateListOf<String>() }
    val exclusions = remember { mutableStateListOf<String>() }
    var preferenceInput by remember { mutableStateOf("") }
    var exclusionInput by remember { mutableStateOf("") }
    var conversationCount by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Loading private memory…") }

    LaunchedEffect(store) {
        runCatching { store.load() }.onSuccess { snapshot ->
            preferences.clear()
            preferences.addAll(snapshot.confirmedPreferences)
            exclusions.clear()
            exclusions.addAll(snapshot.exclusions)
            conversationCount = snapshot.messages.size
            status = "Memory is encrypted and stored locally on this device."
        }.onFailure {
            status = "Memory could not be opened: ${it.message ?: "unknown error"}"
        }
    }

    fun persistLists() {
        scope.launch {
            runCatching {
                val current = store.load()
                store.save(
                    current.copy(
                        confirmedPreferences = preferences.toList(),
                        exclusions = exclusions.toList()
                    )
                )
            }.onSuccess {
                status = "Your changes are saved locally."
            }.onFailure {
                status = "Memory save failed: ${it.message ?: "unknown error"}"
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("YOUR CONTINUITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Memory and preferences", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$conversationCount saved conversation messages", style = MaterialTheme.typography.titleMedium)
                Text("The app owns this state. It is stored encrypted on-device and is independent of the selected model.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("CONFIRMED PREFERENCES", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = preferenceInput,
            onValueChange = { preferenceInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Add a preference you confirm") },
            singleLine = true
        )
        Button(
            onClick = {
                val value = preferenceInput.trim()
                if (value.isNotEmpty() && value !in preferences) {
                    preferences.add(value)
                    preferenceInput = ""
                    persistLists()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save preference") }
        if (preferences.isNotEmpty()) {
            LazyColumn(modifier = Modifier.height(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(preferences.toList()) { value ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(value, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { preferences.remove(value); persistLists() }) { Text("Remove") }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Text("EXPLICIT EXCLUSIONS", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = exclusionInput,
            onValueChange = { exclusionInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Add something the experience must avoid") },
            singleLine = true
        )
        Button(
            onClick = {
                val value = exclusionInput.trim()
                if (value.isNotEmpty() && value !in exclusions) {
                    exclusions.add(value)
                    exclusionInput = ""
                    persistLists()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save exclusion") }
        if (exclusions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.height(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(exclusions.toList()) { value ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(value, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { exclusions.remove(value); persistLists() }) { Text("Remove") }
                    }
                }
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
