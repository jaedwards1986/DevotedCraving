package com.forgeai.chat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgeai.chat.data.local.dao.MemoryDao
import com.forgeai.chat.data.local.entity.MemoryEntity
import com.forgeai.chat.data.memory.MemoryService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManagementScreen(
    memoryDao: MemoryDao,
    onNavigateBack: () -> Unit
) {
    val active by memoryDao.observeActiveMemories().collectAsState(initial = emptyList())
    val pending by memoryDao.observePendingConsent().collectAsState(initial = emptyList())
    val service = remember(memoryDao) { MemoryService(memoryDao) }
    val scope = rememberCoroutineScope()
    var showPending by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var editCategory by remember { mutableStateOf("") }
    var editSubject by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<MemoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Memory", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Your saved preferences and continuity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showPending,
                    onClick = { showPending = false },
                    label = { Text("Saved (${active.size})") },
                    leadingIcon = if (!showPending) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null
                )
                FilterChip(
                    selected = showPending,
                    onClick = { showPending = true },
                    label = { Text("Needs review (${pending.size})") },
                    leadingIcon = if (showPending) {
                        { Icon(Icons.Rounded.Psychology, contentDescription = null) }
                    } else null
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (showPending) {
                    "Review suggestions before they can be used in conversations."
                } else {
                    "Only saved memories are available to the assistant. You can edit or remove any item."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            val entries = if (showPending) pending else active
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (showPending) "Nothing waiting for review" else "No saved memories yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (showPending) "New suggestions will appear here." else "Useful preferences and details can be saved here as you chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entries, key = { it.memoryId }) { memory ->
                        MemoryCard(
                            memory = memory,
                            pending = showPending,
                            onApprove = { scope.launch { service.approve(memory.memoryId) } },
                            onReject = { scope.launch { service.reject(memory.memoryId) } },
                            onEdit = {
                                editing = memory
                                editCategory = memory.category
                                editSubject = memory.subject
                                editContent = memory.content
                            },
                            onDelete = { deleteTarget = memory }
                        )
                    }
                }
            }
        }
    }

    editing?.let { memory ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Correct memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editCategory, { editCategory = it }, label = { Text("Category") }, singleLine = true)
                    OutlinedTextField(editSubject, { editSubject = it }, label = { Text("Subject") }, singleLine = true)
                    OutlinedTextField(editContent, { editContent = it }, label = { Text("What should be remembered?") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    enabled = editCategory.isNotBlank() && editSubject.isNotBlank() && editContent.isNotBlank(),
                    onClick = {
                        scope.launch {
                            service.correct(memory.memoryId, editCategory, editSubject, editContent)
                            editing = null
                        }
                    }
                ) { Text("Save correction") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { memory ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this memory?") },
            text = { Text("“${memory.subject}” will be permanently removed from saved memory.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        service.delete(memory.memoryId)
                        deleteTarget = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    pending: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    memory.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (memory.isPinned) {
                    Text("PINNED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(memory.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(memory.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pending) {
                    TextButton(onClick = onReject) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reject")
                    }
                    Button(onClick = onApprove) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Approve")
                    }
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit memory")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
