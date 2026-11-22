package com.natasha.local

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.room.withTransaction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.natasha.local.data.*
import com.natasha.local.llm.LocalLlmEngine
import com.natasha.local.llm.PromptBuilder
import com.natasha.local.models.ModelHubScreen
import com.natasha.local.maintenance.MaintenanceWorker
import com.natasha.local.projects.ProjectToolService
import com.natasha.local.projects.ProjectBuildService
import com.natasha.local.projects.ProjectBuildPlan
import com.natasha.local.projects.ProjectFileChange
import com.natasha.local.projects.ProjectArchiveService
import com.natasha.local.projects.ProjectActivity
import com.natasha.local.projects.ProjectActivityStore
import com.natasha.local.projects.ProjectContextBuilder
import com.natasha.local.projects.ProjectContextFile
import com.natasha.local.projects.ProjectWorkspace
import com.natasha.local.projects.ProjectPlanParser
import com.natasha.local.projects.WorkspaceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NatashaPersonalityUpdates.installFirstUpdate(this)
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "natasha-maintenance", ExistingPeriodicWorkPolicy.KEEP, request
        )
        setContent { NatashaApp() }
    }
}

private enum class AppTab(val label: String) {
    Chat("Chat"), Projects("Projects"), Models("Models"), Memory("Memory"), Settings("Settings")
}

@Composable
fun NatashaApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val engine = remember { LocalLlmEngine() }
    var tab by rememberSaveable { mutableStateOf(AppTab.Chat) }
    var input by rememberSaveable { mutableStateOf("") }
    var messages by remember { mutableStateOf(emptyList<MessageEntity>()) }
    var status by remember { mutableStateOf("Ready") }
    var startupLoading by remember { mutableStateOf(true) }
    var startupStage by remember { mutableStateOf("Starting ForgeAI…") }
    var startupProgress by remember { mutableStateOf<Float?>(null) }
    var models by remember { mutableStateOf(emptyList<ModelEntity>()) }
    var memories by remember { mutableStateOf(emptyList<MemoryEntity>()) }
    var projects by remember { mutableStateOf(emptyList<ProjectEntity>()) }
    var generating by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var selectedProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var conversationId by rememberSaveable { mutableStateOf(1L) }
    var conversations by remember { mutableStateOf(listOf(1L)) }
    var pendingGeneratedPlan by remember { mutableStateOf<ProjectBuildPlan?>(null) }
    var pendingPlanProjectId by remember { mutableStateOf<Long?>(null) }

    fun refresh() {
        scope.launch {
            messages = db.messages().forConversation(conversationId)
            conversations = db.messages().conversationIds().ifEmpty { listOf(1L) }
            models = db.models().all()
            memories = db.memories().all()
            projects = db.projects().all()
        }
    }

    LaunchedEffect(Unit) {
        startupLoading = true
        startupStage = "Preparing Natasha…"
        startupProgress = null
        try {
            val latestConversation = db.messages().conversationIds().firstOrNull()
            if (latestConversation != null) conversationId = latestConversation
            refresh()
            val selectedBeforeInstall = db.models().selected()
            val needsPreferredModel = selectedBeforeInstall == null || !File(selectedBeforeInstall.path).isFile ||
                selectedBeforeInstall.name.contains("Llama 3.2 1B", ignoreCase = true) ||
                selectedBeforeInstall.path.contains("llama-3.2-1b", ignoreCase = true) ||
                selectedBeforeInstall.name.contains("Lexi Uncensored", ignoreCase = true) ||
                selectedBeforeInstall.path.contains("Llama-3.1-8B-Lexi", ignoreCase = true) ||
                selectedBeforeInstall.name.contains("Aion RP", ignoreCase = true) ||
                selectedBeforeInstall.path.contains("Aion-RP-Llama-3.1-8B", ignoreCase = true) ||
                selectedBeforeInstall.name.contains("Dirty Harry", ignoreCase = true) ||
                selectedBeforeInstall.path.contains("L3.1-RP-Hero-Dirty_Harry", ignoreCase = true)
            val selected = if (needsPreferredModel) {
                status = "Preparing Natasha's included model…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        ensurePreferredModel(
                            context, db,
                            onStage = { stage -> startupStage = stage; status = stage; startupProgress = null },
                            onProgress = { downloaded, total ->
                                startupProgress = if (total > 0L) downloaded.toFloat() / total.toFloat() else null
                                startupStage = if (total > 0L) "Installing included model · " +
                                    "%.0f".format(downloaded.toDouble() * 100.0 / total.toDouble()) + "%"
                                    else "Preparing included model…"
                                status = startupStage
                            }
                        )
                    }
                }.getOrElse {
                    status = "Model setup failed: " + (it.message ?: "unknown error")
                    startupStage = status
                    selectedBeforeInstall
                }
            } else selectedBeforeInstall

            if (selected != null) {
                startupStage = "Loading model into memory…"
                startupProgress = null
                status = "Loading " + selected.name + " into memory…"
                val ok = try {
                    withContext(Dispatchers.Default) {
                        engine.load(selected.path, contextSize = 2048, threads = 4)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    // Native library/linker failures must not take down the entire Compose app.
                    // Keep the chat UI available and expose the actual reason in the status line.
                    status = "Model load failed: " + (failure.message ?: failure.javaClass.simpleName)
                    startupStage = status
                    false
                }
                if (ok) {
                    status = "Ready · " + selected.name
                    startupStage = status
                } else if (!status.startsWith("Model load failed:")) {
                    status = "Model failed to load. Open Models to retry or choose another GGUF."
                    startupStage = status
                }
            } else if (status.startsWith("Ready")) {
                status = "Ready · import a GGUF model"
                startupStage = status
            }
            refresh()
        } finally {
            startupLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.cancel()
            scope.launch { engine.unload() }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6EA8FF),
            onPrimary = Color(0xFF07101F),
            secondary = Color(0xFF9CC2FF),
            onSecondary = Color(0xFF07101F),
            background = Color(0xFF0B1220),
            onBackground = Color(0xFFF4F7FF),
            surface = Color(0xFF111B2E),
            onSurface = Color(0xFFF4F7FF),
            surfaceVariant = Color(0xFF1B2A46),
            onSurfaceVariant = Color(0xFFAFC7F5)
        )
    ) {
        Scaffold(
            containerColor = Color(0xFF0B1220),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0B1220)) {
                    AppTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Text(item.label.take(1), fontWeight = FontWeight.Medium) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFF4F7FF),
                                selectedTextColor = Color(0xFFF4F7FF),
                                indicatorColor = Color(0xFF243B63),
                                unselectedIconColor = Color(0xFF7F96B8),
                                unselectedTextColor = Color(0xFF7F96B8)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    AppTab.Chat -> ChatScreen(
                        messages = messages,
                        input = input,
                        onInput = { input = it },
                        status = status,
                        generating = generating,
                        onCancel = {
                            engine.cancel()
                            generationJob?.cancel()
                            generating = false
                            status = "Generation stopped"
                        },
                        onSend = {
                            if (!input.isBlank() && !generating) {
                                val text = input.trim()
                                input = ""
                                val targetConversationId = conversationId
                                generating = true
                                status = "Natasha is thinking…"
                                generationJob = scope.launch {
                                    val assistant = StringBuilder()
                                    try {
                                        // Persist and display the user's message before model preparation.
                                        db.messages().insert(
                                            MessageEntity(conversationId = targetConversationId, role = "user", content = text)
                                        )
                                        if (conversationId == targetConversationId) {
                                            messages = db.messages().forConversation(targetConversationId)
                                        }
                                        val selectedModel = db.models().selected()
                                        if (selectedModel == null) {
                                            status = "No AI model is selected. Open Models and install or select Natasha's model."
                                            return@launch
                                        }
                                        if (!File(selectedModel.path).isFile) {
                                            status = "The selected model file is missing. Open Models and verify or reinstall it."
                                            return@launch
                                        }
                                        val profile = db.character().get() ?: CharacterProfileEntity()
                                        // Capture explicit user facts before retrieval so a newly learned fact
                                        // is available to the very response that learned it.
                                        MemoryCapture.extract(text).forEach { candidate ->
                                            val normalized = candidate.trim().lowercase()
                                            val exists = db.memories().all().any { it.text.trim().lowercase() == normalized }
                                            if (!exists) db.memories().insert(MemoryEntity(text = candidate, kind = "auto"))
                                        }
                                        val allMemories = db.memories().all()
                                        val recallWords = text.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
                                        val rankedMemories = allMemories.map { memory ->
                                            val haystack = memory.text.lowercase()
                                            val score = recallWords.count { haystack.contains(it) } * 10 +
                                                if (memory.kind.equals("pinned", true)) 100 else 0
                                            score to memory
                                        }.sortedByDescending { it.first }
                                        val matchingMemories = rankedMemories.filter { it.first > 0 }.map { it.second }
                                        val memoriesForPrompt = if (matchingMemories.isNotEmpty()) matchingMemories
                                            else allMemories.sortedByDescending { it.updatedAt }.take(32)
                                        val history = db.messages().forConversation(targetConversationId).takeLast(64)
                                        memories = allMemories
                                        if (conversationId == targetConversationId) {
                                            messages = db.messages().forConversation(targetConversationId)
                                        }

                                        val selectedModelName = db.models().selected()?.name.orEmpty()
                                        val activeProject = selectedProjectId?.let { id ->
                                            projects.firstOrNull { it.id == id } ?: db.projects().get(id)
                                        }
                                        val projectContext = if (activeProject == null) "" else withContext(Dispatchers.IO) {
                                            val workspace = ProjectWorkspace(context, activeProject.id)
                                            val sourceFiles = workspace.list()
                                                .filter { ProjectContextBuilder.isEligiblePath(it) }
                                                .take(12)
                                                .mapNotNull { path ->
                                                    runCatching {
                                                        ProjectContextFile(path, workspace.read(path, 64 * 1024))
                                                    }.getOrNull()
                                                }
                                            ProjectContextBuilder.build(
                                                activeProject.name,
                                                activeProject.description,
                                                sourceFiles
                                            )
                                        }
                                        val prompt = PromptBuilder.build(
                                            profile, memoriesForPrompt, history, text, selectedModelName, projectContext,
                                            NatashaPersonalityUpdates.promptFor(context, selectedModelName)
                                        )
                                        engine.stream(prompt).collect { token ->
                                            assistant.append(token)
                                            if (conversationId == targetConversationId) {
                                                messages = messages.filterNot { it.id == -1L } + MessageEntity(
                                                    id = -1L,
                                                    conversationId = targetConversationId,
                                                    role = "assistant",
                                                    content = assistant.toString()
                                                )
                                            }
                                        }
                                        val completeResponse = assistant.toString()
                                        val proposedPlan = ProjectPlanParser.parse(completeResponse)
                                        if (proposedPlan != null && activeProject != null) {
                                            pendingGeneratedPlan = proposedPlan
                                            pendingPlanProjectId = activeProject.id
                                        }
                                        val visibleResponse = completeResponse
                                            .replace(Regex("(?s)<forgeai_plan>.*?</forgeai_plan>"), "")
                                            .trim()
                                        if (visibleResponse.isNotEmpty()) {
                                            db.messages().insert(
                                                MessageEntity(
                                                    conversationId = targetConversationId,
                                                    role = "assistant",
                                                    content = visibleResponse
                                                )
                                            )
                                        }
                                        if (conversationId == targetConversationId) {
                                            messages = db.messages().forConversation(targetConversationId)
                                            status = "Ready"
                                        }
                                        refresh()
                                    } catch (cancelled: CancellationException) {
                                        if (conversationId == targetConversationId) {
                                            messages = messages.filterNot { it.id == -1L }
                                            status = "Generation stopped"
                                        }
                                        throw cancelled
                                    } catch (failure: Throwable) {
                                        if (conversationId == targetConversationId) {
                                            messages = db.messages().forConversation(targetConversationId)
                                            status = "Request failed: " + (failure.message ?: "unknown error")
                                        }
                                    } finally {
                                        generating = false
                                    }
                                }
                            }
                        },
                        onRefresh = { refresh() },
                        onNewChat = {
                            conversationId = (conversations.maxOrNull() ?: 0L) + 1L
                            messages = emptyList()
                            status = "New chat"
                        },
                        onConversationSelected = { id ->
                            conversationId = id
                            scope.launch { messages = db.messages().forConversation(id) }
                        },
                        conversations = conversations,
                        pendingPlanSummary = pendingGeneratedPlan?.summary,
                        onReviewPlan = {
                            pendingPlanProjectId?.let { selectedProjectId = it }
                            tab = AppTab.Projects
                        }
                    )
                    AppTab.Projects -> ProjectsScreen(
                        projects, selectedProjectId, { selectedProjectId = it }, db, scope, ::refresh,
                        pendingGeneratedPlan,
                        pendingPlanProjectId,
                        {
                            pendingGeneratedPlan = null
                            pendingPlanProjectId = null
                        }
                    )
                    AppTab.Models -> ModelsScreen(models, db, scope, engine, ::refresh) { status = it }
                    AppTab.Memory -> MemoryScreen(memories, db, scope, ::refresh)
                    AppTab.Settings -> SettingsScreen(status)
                }

                if (startupLoading) {
                    Box(Modifier.fillMaxSize().background(Color(0xF20B1220)), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                            color = Color(0xFF111B2E),
                            shape = RoundedCornerShape(22.dp),
                            tonalElevation = 8.dp
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Natasha is getting ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFFF4F7FF))
                                Text(startupStage, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9CBE7))
                                if (startupProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { startupProgress!!.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF6EA8FF),
                                        trackColor = Color(0xFF263B5B)
                                    )
                                    Text("%.0f%%".format(startupProgress!!.coerceIn(0f, 1f) * 100f), style = MaterialTheme.typography.titleMedium, color = Color(0xFFF4F7FF))
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF6EA8FF),
                                        trackColor = Color(0xFF263B5B)
                                    )
                                }
                                Text(
                                    "The first download can take a while. Keep ForgeAI open while the model downloads and loads.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8EA6C8)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<MessageEntity>,
    input: String,
    onInput: (String) -> Unit,
    status: String,
    generating: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onNewChat: () -> Unit,
    onConversationSelected: (Long) -> Unit,
    conversations: List<Long>,
    pendingPlanSummary: String?,
    onReviewPlan: () -> Unit
) {
    val context = LocalContext.current
    var attachmentLabel by rememberSaveable { mutableStateOf("") }
    val attachmentPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val name = queryDisplayName(context, uri) ?: "attachment.txt"
                val extension = name.substringAfterLast('.', "").lowercase()
                require(extension in setOf("txt", "md", "kt", "kts", "java", "py", "js", "ts", "json", "xml", "html", "css", "csv", "yaml", "yml", "gradle", "properties", "sql")) {
                    "Choose a text or source-code file"
                }
                require((querySize(context, uri) ?: 0L) <= 8_000L) { "Attachment exceeds the 8 KB text limit" }
                val content = readUriTextLimited(context, uri, 8_000)
                attachmentLabel = name
                onInput((input + "\n\n[Attached file: " + name + "]\n" + content).trim())
            }.onFailure { attachmentLabel = "Attachment failed: " + (it.message ?: "unknown error") }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B1220)).padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("ForgeAI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8EA6C8))
            }
            TextButton(onClick = onRefresh) { Text("Refresh", color = Color(0xFFBDBDBD)) }
            TextButton(onClick = onNewChat) { Text("New chat", color = Color(0xFFF4F7FF)) }
        }

        if (pendingPlanSummary != null) {
            Surface(
                color = Color(0xFF303B49),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Project changes ready for review", fontWeight = FontWeight.SemiBold)
                        Text(pendingPlanSummary, color = Color(0xFFB8C6D6), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onReviewPlan) { Text("Review") }
                }
            }
        }

        if (conversations.size > 1) {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 70.dp),
                horizontalAlignment = Alignment.End
            ) {
                items(conversations, key = { it }) { id ->
                    TextButton(onClick = { onConversationSelected(id) }) {
                        Text(if (id == 1L) "Chat 1" else "Chat $id")
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("What’s on your mind?", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFF4F7FF))
                    Spacer(Modifier.height(8.dp))
                    Text("Your private space to think, create, and talk.", color = Color(0xFF8EA6C8))
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { if (it.id > 0) it.id else "stream-" + it.content.hashCode() }) { message ->
                    val user = message.role == "user"
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (user) Color(0xFF3A3A3A) else Color.Transparent,
                            shape = RoundedCornerShape(if (user) 20.dp else 8.dp)
                        ) {
                            Text(
                                message.content,
                                Modifier.padding(horizontal = if (user) 16.dp else 2.dp, vertical = 11.dp),
                                color = Color(0xFFE6E6E6)
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedButton(
                onClick = { attachmentPicker.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "*/*")) },
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Natasha…", color = Color(0xFF929292)) },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF777777),
                    unfocusedBorderColor = Color(0xFF555555),
                    focusedTextColor = Color(0xFFE6E6E6),
                    unfocusedTextColor = Color(0xFFE6E6E6),
                    cursorColor = Color(0xFFE6E6E6)
                )
            )
            Spacer(Modifier.width(8.dp))
            if (generating) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.height(52.dp)) { Text("Stop") }
            } else {
                Button(onClick = onSend, modifier = Modifier.height(52.dp)) { Text("Send") }
            }
        }
    }
}

@Composable
private fun ProjectsScreen(projects: List<ProjectEntity>, selectedProjectId: Long?, onSelect: (Long?) -> Unit, db: AppDatabase, scope: CoroutineScope, refresh: () -> Unit, incomingPlan: ProjectBuildPlan?, incomingPlanProjectId: Long?, onPlanConsumed: () -> Unit) {
    val context = LocalContext.current
    val tools = remember { ProjectToolService(context) }
    val buildService = remember { ProjectBuildService(context) }
    val archiveService = remember { ProjectArchiveService(context) }
    val latestSelectedProjectId by rememberUpdatedState(selectedProjectId)
    var archiveStatus by rememberSaveable { mutableStateOf("") }
    var recentActivity by remember { mutableStateOf(emptyList<ProjectActivity>()) }
    var projectFiles by remember { mutableStateOf(emptyList<WorkspaceFile>()) }
    var filesRefreshKey by remember { mutableIntStateOf(0) }
    val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val id = selectedProjectId
        if (uri != null && id != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("Unable to open export destination")
                    output.use { archiveService.export(id, it) }
                }
                archiveStatus = if (result.ok) "Exported ${result.value} files" else result.error.orEmpty()
                if (result.ok) recentActivity = withContext(Dispatchers.IO) { ProjectActivityStore(context, id).recent(12) }
            }
        }
    }
    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val id = selectedProjectId
        if (uri != null && id != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Unable to open selected archive")
                    input.use { archiveService.import(id, it) }
                }
                archiveStatus = if (result.ok) "Imported ${result.value} files" else result.error.orEmpty()
                if (result.ok) {
                    recentActivity = withContext(Dispatchers.IO) { ProjectActivityStore(context, id).recent(12) }
                    filesRefreshKey++
                    refresh()
                }
            }
        }
    }
    LaunchedEffect(selectedProjectId, filesRefreshKey) {
        val id = selectedProjectId
        projectFiles = if (id == null) emptyList() else withContext(Dispatchers.IO) {
            tools.listFiles(id).value.orEmpty()
        }
    }
    var buildSummary by rememberSaveable { mutableStateOf("") }
    var buildChanges by remember { mutableStateOf<List<ProjectFileChange>>(emptyList()) }
    var buildSelectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var buildPath by rememberSaveable { mutableStateOf("") }
    var buildContent by rememberSaveable { mutableStateOf("") }
    var showBuildPlan by rememberSaveable { mutableStateOf(false) }
    var buildPreview by remember { mutableStateOf<List<String>?>(null) }
    var buildStatus by rememberSaveable { mutableStateOf("") }
    var newFilePath by rememberSaveable { mutableStateOf("") }
    var newFileContent by rememberSaveable { mutableStateOf("") }
    var showNewFile by rememberSaveable { mutableStateOf(false) }
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var editorContent by rememberSaveable { mutableStateOf("") }
    var editorStatus by rememberSaveable { mutableStateOf("") }
    var editingProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var projectNameDraft by rememberSaveable { mutableStateOf("") }
    var projectDescriptionDraft by rememberSaveable { mutableStateOf("") }
    var pendingDeletePath by rememberSaveable { mutableStateOf<String?>(null) }
    var renameSourcePath by rememberSaveable { mutableStateOf("") }
    var renameDestinationPath by rememberSaveable { mutableStateOf("") }
    var showRenameFile by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedProjectId) {
        val id = selectedProjectId
        selectedFile = null
        editorContent = ""
        editorStatus = ""
        recentActivity = if (id == null) emptyList() else withContext(Dispatchers.IO) {
            ProjectActivityStore(context, id).recent(12)
        }
    }
    LaunchedEffect(incomingPlan, incomingPlanProjectId, selectedProjectId) {
        if (incomingPlan != null && incomingPlanProjectId != null && selectedProjectId == incomingPlanProjectId) {
            buildSummary = incomingPlan.summary
            buildChanges = incomingPlan.changes
            buildSelectedIndex = 0
            buildPath = incomingPlan.changes.first().path
            buildContent = incomingPlan.changes.first().content
            buildPreview = null
            buildStatus = "Natasha prepared this proposal. Review every file before applying."
            showBuildPlan = true
        }
    }


    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        Text("Keep app ideas, files, decisions and unfinished work together.", color = Color(0xFF8EA6C8))
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            scope.launch {
                val id = db.projects().insert(ProjectEntity(name = "New project"))
                withContext(Dispatchers.IO) {
                    ProjectActivityStore(context, id).record("PROJECT_CREATED", "", "New project created")
                }
                refresh()
            }
        }) { Text("New project") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { p ->
                val isSelected = selectedProjectId == p.id
                val files = if (isSelected) projectFiles else emptyList()
                Surface(
                    color = if (isSelected) Color(0xFF3A3A3A) else Color(0xFF111B2E),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { onSelect(if (isSelected) null else p.id) }
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            p.description.ifBlank { "Ready for requirements and development notes." },
                            color = Color(0xFF8EA6C8)
                        )
                        if (isSelected) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Project files", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    editingProjectId = p.id
                                    projectNameDraft = p.name
                                    projectDescriptionDraft = p.description
                                }) { Text("Details") }
                                TextButton(onClick = {
                                    buildChanges = emptyList()
                                    buildSelectedIndex = 0
                                    buildSummary = ""
                                    buildPath = ""
                                    buildContent = ""
                                    showBuildPlan = true
                                    buildPreview = null
                                }) { Text("Build plan") }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { exportPicker.launch("${p.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.zip") }) { Text("Export ZIP") }
                                TextButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Import ZIP") }
                                TextButton(onClick = { showNewFile = true }) { Text("New file") }
                            }
                            if (archiveStatus.isNotBlank()) {
                                Text(archiveStatus, color = Color(0xFFF4F7FF), style = MaterialTheme.typography.bodySmall)
                            }
                            if (recentActivity.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Recent activity", fontWeight = FontWeight.SemiBold)
                                recentActivity.take(8).forEach { event ->
                                    Text(
                                        "${event.action} · ${event.path.ifBlank { event.detail }}",
                                        color = Color(0xFF8EA6C8),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (files.isEmpty()) {
                                Text("No files yet.", color = Color(0xFF8EA6C8))
                            } else {
                                files.take(30).forEach { file ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            file.path,
                                            Modifier.weight(1f),
                                            color = Color(0xFFB8D8FF)
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) { tools.readFile(p.id, file.path) }
                                                if (latestSelectedProjectId == p.id) {
                                                    selectedFile = file.path
                                                    editorContent = result.value.orEmpty()
                                                    editorStatus = result.error.orEmpty()
                                                }
                                            }
                                        }) { Text("Edit") }
                                        TextButton(onClick = { renameSourcePath = file.path; renameDestinationPath = file.path; showRenameFile = true }) { Text("Move") }
                                        TextButton(onClick = { pendingDeletePath = file.path }) { Text("Delete") }
                                    }
                                }
                            }
                            if (selectedFile != null) {
                                Spacer(Modifier.height(8.dp))
                                Text("Editing: " + selectedFile, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = editorContent,
                                    onValueChange = { editorContent = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 320.dp),
                                    maxLines = 16
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = {
                                        val path = selectedFile ?: return@Button
                                        val content = editorContent
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                tools.writeFile(p.id, path, content)
                                            }
                                            if (latestSelectedProjectId == p.id) {
                                                editorStatus = if (result.ok) "Saved" else result.error.orEmpty()
                                                if (result.ok) {
                                                    recentActivity = withContext(Dispatchers.IO) { ProjectActivityStore(context, p.id).recent(12) }
                                                    filesRefreshKey++
                                                }
                                            }
                                        }
                                    }) { Text("Save") }
                                    Spacer(Modifier.width(8.dp))
                                    Text(editorStatus, color = Color(0xFF8EA6C8))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Natasha can read, create, edit and safely delete files inside this project workspace.",
                                color = Color(0xFF8EA6C8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    editingProjectId?.let { projectId ->
        val project = projects.firstOrNull { it.id == projectId }
        AlertDialog(
            onDismissRequest = { editingProjectId = null },
            title = { Text("Project details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = projectNameDraft,
                        onValueChange = { projectNameDraft = it },
                        label = { Text("Project name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = projectDescriptionDraft,
                        onValueChange = { projectDescriptionDraft = it },
                        label = { Text("Description and requirements") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (project != null && projectNameDraft.isNotBlank()) {
                        scope.launch {
                            db.projects().update(project.copy(
                                name = projectNameDraft.trim(),
                                description = projectDescriptionDraft.trim(),
                                updatedAt = System.currentTimeMillis()
                            ))
                            ProjectActivityStore(context, projectId).record("PROJECT_DETAILS", "", "Project details updated")
                            editingProjectId = null
                            refresh()
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingProjectId = null }) { Text("Cancel") } }
        )
    }

    if (showRenameFile) {
        AlertDialog(
            onDismissRequest = { showRenameFile = false },
            title = { Text("Move or rename project file") },
            text = {
                Column {
                    Text("Choose a new project-relative path. Existing files will never be overwritten.", color = Color(0xFF8EA6C8))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameDestinationPath,
                        onValueChange = { renameDestinationPath = it },
                        label = { Text("New file path") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = selectedProjectId
                    val destination = renameDestinationPath.trim()
                    if (id != null && destination.isNotBlank()) {
                        scope.launch {
                            val source = renameSourcePath
                            val result = withContext(Dispatchers.IO) {
                                tools.renameFile(id, source, destination)
                            }
                            if (latestSelectedProjectId == id) {
                                editorStatus = if (result.ok) "Moved to $destination" else result.error.orEmpty()
                                if (result.ok && selectedFile == source) selectedFile = destination
                                if (result.ok) {
                                    recentActivity = withContext(Dispatchers.IO) { ProjectActivityStore(context, id).recent(12) }
                                    filesRefreshKey++
                                }
                                showRenameFile = false
                                refresh()
                            }
                        }
                    } else {
                        editorStatus = "Enter a valid project-relative destination."
                    }
                }) { Text("Move file") }
            },
            dismissButton = { TextButton(onClick = { showRenameFile = false }) { Text("Cancel") } }
        )
    }

    pendingDeletePath?.let { pathToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeletePath = null },
            title = { Text("Delete project file?") },
            text = { Text("This permanently deletes $pathToDelete from this project's private workspace.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = selectedProjectId
                    if (id != null) {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { tools.deleteFile(id, pathToDelete) }
                            editorStatus = if (result.ok) "Deleted $pathToDelete" else result.error.orEmpty()
                            if (result.ok) {
                                recentActivity = withContext(Dispatchers.IO) { ProjectActivityStore(context, id).recent(12) }
                                filesRefreshKey++
                            }
                            if (selectedFile == pathToDelete) selectedFile = null
                            pendingDeletePath = null
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeletePath = null }) { Text("Keep file") } }
        )
    }

    if (showBuildPlan) {
        AlertDialog(
            onDismissRequest = { showBuildPlan = false; buildPreview = null; buildChanges = emptyList(); buildSelectedIndex = 0; onPlanConsumed() },
            title = { Text("Prepare project changes") },
            text = {
                Column {
                    Text("Review the proposed file change before Natasha writes it.", color = Color(0xFF8EA6C8))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = buildSummary,
                        onValueChange = { buildSummary = it; buildPreview = null },
                        label = { Text("What are we building?") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = buildPath,
                        onValueChange = { value ->
                            buildPath = value
                            buildPreview = null
                            if (buildChanges.isNotEmpty() && buildSelectedIndex in buildChanges.indices) {
                                buildChanges = buildChanges.mapIndexed { index, change ->
                                    if (index == buildSelectedIndex) change.copy(path = value) else change
                                }
                            }
                        },
                        label = { Text("Project-relative file path") },
                        placeholder = { Text("app/src/Main.kt") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = buildContent,
                        onValueChange = { value ->
                            buildContent = value
                            buildPreview = null
                            if (buildChanges.isNotEmpty() && buildSelectedIndex in buildChanges.indices) {
                                buildChanges = buildChanges.mapIndexed { index, change ->
                                    if (index == buildSelectedIndex) change.copy(content = value) else change
                                }
                            }
                        },
                        label = { Text("Complete file contents") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 280.dp),
                        maxLines = 16
                    )
                    if (buildChanges.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text("Review each proposed file:", fontWeight = FontWeight.SemiBold)
                        buildChanges.forEachIndexed { index, change ->
                            TextButton(
                                onClick = {
                                    buildSelectedIndex = index
                                    buildPath = change.path
                                    buildContent = change.content
                                    buildPreview = null
                                }
                            ) {
                                Text(
                                    (if (index == buildSelectedIndex) "Reviewing · " else "Review · ") + change.path,
                                    color = if (index == buildSelectedIndex) Color(0xFFF4F7FF) else Color(0xFFB8D8FF)
                                )
                            }
                        }
                    }
                    if (buildPreview != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Preview ready for: " + buildPreview!!.joinToString(), color = Color(0xFFF4F7FF))
                    }
                    if (buildStatus.isNotBlank()) {
                        Text(buildStatus, color = Color(0xFF8EA6C8))
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val id = selectedProjectId
                        if (id != null) {
                            val plan = ProjectBuildPlan(buildSummary, buildChanges.ifEmpty { listOf(ProjectFileChange(buildPath, buildContent)) })
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { buildService.preview(id, plan) }
                                buildPreview = if (result.ok) result.value else null
                                buildStatus = if (result.ok) "Preview ready. Apply only when the paths and contents are correct." else result.error.orEmpty()
                            }
                        } else {
                            buildStatus = "Select a project first."
                        }
                    }) { Text("Preview") }
                    TextButton(
                        enabled = buildPreview != null && selectedProjectId != null,
                        onClick = {
                            val id = selectedProjectId
                            if (id != null) {
                                val plan = ProjectBuildPlan(buildSummary, buildChanges.ifEmpty { listOf(ProjectFileChange(buildPath, buildContent)) })
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { buildService.apply(id, plan) }
                                    buildStatus = if (result.ok) "Applied: " + result.value.orEmpty().joinToString() else result.error.orEmpty()
                                    if (result.ok) {
                                        showBuildPlan = false
                                        buildPreview = null
                                        buildChanges = emptyList()
                                        onPlanConsumed()
                                        filesRefreshKey++
                                        refresh()
                                    }
                                }
                            }
                        }
                    ) { Text("Apply changes") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuildPlan = false; buildPreview = null; buildChanges = emptyList(); buildSelectedIndex = 0; onPlanConsumed() }) { Text("Cancel") }
            }
        )
    }

    if (showNewFile) {
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            title = { Text("Create project file") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFilePath,
                        onValueChange = { newFilePath = it },
                        label = { Text("Path") },
                        placeholder = { Text("src/Main.kt") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = { Text("Contents") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                        maxLines = 12
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = selectedProjectId
                    val path = newFilePath.trim()
                    val content = newFileContent
                    if (id != null && path.isNotBlank()) {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { tools.createFile(id, path, content) }
                            if (latestSelectedProjectId == id) {
                                editorStatus = if (result.ok) "Created $path" else result.error.orEmpty()
                                if (result.ok) {
                                    newFilePath = ""
                                    newFileContent = ""
                                    showNewFile = false
                                    filesRefreshKey++
                                }
                            }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFile = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModelsScreen(
    models: List<ModelEntity>,
    db: AppDatabase,
    scope: CoroutineScope,
    engine: LocalLlmEngine,
    refresh: () -> Unit,
    setStatus: (String) -> Unit
) {
    val context = LocalContext.current
    var pendingRemoval by remember { mutableStateOf<ModelEntity?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyModelIntoApp(context, uri) }
                val hash = withContext(Dispatchers.IO) { sha256(file) }
                val hadSelectedModel = db.models().selected() != null
                db.models().save(
                    ModelEntity(
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = file.length(),
                        sha256 = hash,
                        quantization = detectQuantization(file.name),
                        selected = false
                    )
                )
                val loaded = if (!hadSelectedModel) {
                    withContext(Dispatchers.Default) { engine.load(file.absolutePath) }
                } else false
                if (loaded) {
                    db.models().clearSelection()
                    db.models().select(file.absolutePath)
                }
                refresh()
                setStatus(if (loaded) "Imported and loaded " + file.name else "Imported " + file.name + if (hadSelectedModel) "" else " · load failed; model remains available")
            } catch (t: Throwable) {
                setStatus("Import failed: " + (t.message ?: "unknown error"))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Models", style = MaterialTheme.typography.headlineMedium)
        Text("Switch the AI engine without changing Natasha's identity or stored data.", color = Color(0xFF8EA6C8))
        Text("Available storage: " + formatBytes(android.os.StatFs(context.filesDir.path).availableBytes), color = Color(0xFF8EA6C8))
        Spacer(Modifier.height(14.dp))
        Button(onClick = { picker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }) {
            Text("Import GGUF")
        }
        ModelHubScreen { downloadedFile ->
            scope.launch {
                try {
                    val hash = withContext(Dispatchers.IO) { sha256(downloadedFile) }
                    val hadSelectedModel = db.models().selected() != null
                    db.models().save(
                        ModelEntity(
                            path = downloadedFile.absolutePath,
                            name = downloadedFile.name,
                            sizeBytes = downloadedFile.length(),
                            sha256 = hash,
                            quantization = detectQuantization(downloadedFile.name),
                            selected = false
                        )
                    )
                    val loaded = if (!hadSelectedModel) {
                        withContext(Dispatchers.Default) { engine.load(downloadedFile.absolutePath) }
                    } else false
                    if (loaded) {
                        db.models().clearSelection()
                        db.models().select(downloadedFile.absolutePath)
                    }
                    refresh()
                    setStatus(if (loaded) "Downloaded, verified and loaded ${downloadedFile.name}" else "Downloaded and verified ${downloadedFile.name}; choose Use to load it")
                } catch (failure: Throwable) {
                    setStatus("Model registration failed: " + (failure.message ?: "unknown error"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models, key = { it.path }) { model ->
                Surface(color = Color(0xFF111B2E), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(model.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    formatBytes(model.sizeBytes) + " · " + model.quantization + " · " +
                                        if (model.selected) "Selected" else "Available",
                                    color = if (model.selected) Color(0xFFBDBDBD) else Color(0xFF8EA6C8)
                                )
                            }
                            if (!model.selected) {
                                TextButton(onClick = {
                                    scope.launch {
                                        setStatus("Loading " + model.name + "…")
                                        val previous = db.models().selected()
                                        val file = managedModelFile(context, model.path)
                                        if (file == null || !file.isFile) {
                                            setStatus("Model file is missing or outside app storage")
                                        } else {
                                            engine.unload()
                                            val ok = withContext(Dispatchers.Default) { engine.load(file.absolutePath) }
                                            if (ok) {
                                                db.models().clearSelection()
                                                db.models().select(model.path)
                                                setStatus("Ready · " + model.name)
                                            } else {
                                                val oldFile = previous?.let { managedModelFile(context, it.path) }
                                                val restored = if (oldFile != null && oldFile.isFile) {
                                                    withContext(Dispatchers.Default) { engine.load(oldFile.absolutePath) }
                                                } else false
                                                setStatus(if (restored) "Unable to load " + model.name + "; previous model restored" else "Model load failed; no model is active")
                                            }
                                            refresh()
                                        }
                                    }
                                }) { Text("Use") }
                            }
                        }
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val file = managedModelFile(context, model.path)
                                        if (file == null || !file.isFile) "Missing or unsafe model file"
                                        else if (file.length() != model.sizeBytes) "Size mismatch"
                                        else if (model.sha256.isNotBlank() && sha256(file) != model.sha256) "Integrity check failed"
                                        else "Integrity verified"
                                    }
                                    setStatus(model.name + ": " + result)
                                }
                            }) { Text("Verify") }
                            TextButton(onClick = { pendingRemoval = model }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove model?") },
            text = { Text("Remove ${model.name} from this device? Its GGUF file will be deleted from Natasha's private model storage.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val file = managedModelFile(context, model.path)
                        if (file == null) {
                            setStatus("Refusing to remove a model outside app storage")
                        } else {
                            if (model.selected) engine.unload()
                            val deleted = !file.exists() || withContext(Dispatchers.IO) { file.delete() }
                            if (deleted) {
                                db.models().delete(model)
                                if (model.selected) db.models().clearSelection()
                                setStatus("Removed " + model.name)
                            } else {
                                if (model.selected && file.exists()) {
                                    val restored = withContext(Dispatchers.Default) { engine.load(file.absolutePath) }
                                    setStatus(if (restored) "Unable to delete model file; previous model restored" else "Unable to delete model file; model reload failed")
                                } else {
                                    setStatus("Unable to delete model file")
                                }
                            }
                            refresh()
                        }
                        pendingRemoval = null
                    }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MemoryScreen(memories: List<MemoryEntity>, db: AppDatabase, scope: CoroutineScope, refresh: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<MemoryArchive?>(null) }
    var archiveStatus by rememberSaveable { mutableStateOf("") }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val editing = memories.firstOrNull { it.id == editingId }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    MemoryArchiveCodec.encode(db.messages().all(), db.memories().all())
                }
                require(json.length <= 50_000_000) { "Archive exceeds the 50 MB import safety limit" }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Unable to write archive")
                }
                archiveStatus = "Archive exported."
            }.onFailure { archiveStatus = "Export failed: " + (it.message ?: "unknown error") }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val archive = withContext(Dispatchers.IO) {
                    MemoryArchiveCodec.decode(readUriTextLimited(context, uri, 50_000_000))
                }
                pendingImport = archive
            }.onFailure { archiveStatus = "Import failed: " + (it.message ?: "invalid archive") }
        }
    }

    val visible = if (query.isBlank()) memories else memories.filter {
        it.text.contains(query, ignoreCase = true) || it.kind.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Memory", style = MaterialTheme.typography.headlineMedium)
        Text("Long-term memory archive · ${memories.size} saved memories. Stored locally and retained until you delete them.", color = Color(0xFF8EA6C8))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search memory") }
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Button(onClick = {
                scope.launch {
                    db.memories().insert(MemoryEntity(text = "New memory"))
                    refresh()
                }
            }) { Text("Add memory") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { showClearConfirmation = true }) { Text("Clear all") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("forgeai-memory-archive.json") }) { Text("Export archive") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json")) }) { Text("Import archive") }
        }
        if (archiveStatus.isNotBlank()) {
            Text(archiveStatus, color = Color(0xFF8EA6C8), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { memory ->
                Surface(color = Color(0xFF111B2E), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(memory.text)
                            Text(memory.kind, color = Color(0xFF8EA6C8), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                db.memories().update(memory.copy(
                                    kind = if (memory.kind == "pinned") "auto" else "pinned",
                                    updatedAt = System.currentTimeMillis()
                                ))
                                refresh()
                            }
                        }) { Text(if (memory.kind == "pinned") "Unpin" else "Pin") }
                        TextButton(onClick = {
                            editingId = memory.id
                            draft = memory.text
                        }) { Text("Edit") }
                        TextButton(onClick = {
                            scope.launch {
                                db.memories().delete(memory)
                                refresh()
                            }
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Delete all saved memories?") },
            text = { Text("This permanently deletes the memory archive from this device. Export a backup first if you may need it.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { db.memories().clear(); showClearConfirmation = false; refresh() }
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") } }
        )
    }
    pendingImport?.let { archive ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore ForgeAI archive?") },
            text = { Text("Import ${archive.messages.size} messages and ${archive.memories.size} memories. Existing conversations stay in place; duplicate memories are skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            db.withTransaction {
                                val existing = db.memories().all().map { it.text.trim().lowercase() }.toMutableSet()
                                val additions = archive.memories.filter { existing.add(it.text.trim().lowercase()) }
                                if (archive.messages.isNotEmpty()) {
                                    val firstNewId = db.messages().maxConversationId() + 1L
                                    val conversationMap = archive.messages.map { it.conversationId }.distinct()
                                        .sorted().mapIndexed { index, oldId -> oldId to (firstNewId + index) }.toMap()
                                    val safeMessages = archive.messages.map { item ->
                                        item.copy(id = 0L, conversationId = conversationMap.getValue(item.conversationId))
                                    }
                                    db.messages().insertAll(safeMessages)
                                }
                                if (additions.isNotEmpty()) db.memories().insertAll(additions)
                            }
                        }.onSuccess {
                            pendingImport = null
                            archiveStatus = "Archive restored."
                            refresh()
                        }.onFailure {
                            archiveStatus = "Restore failed: " + (it.message ?: "database error")
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
        )
    }
    editing?.let { memory ->
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text("Edit memory") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 10_000) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = draft.trim()
                    if (text.isBlank() || text.length > 10_000) {
                        archiveStatus = "Memory text must contain 1 to 10,000 characters."
                    } else {
                        scope.launch {
                            db.memories().update(memory.copy(text = text, updatedAt = System.currentTimeMillis()))
                            editingId = null
                            refresh()
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsScreen(status: String) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("forgeai_maintenance", Context.MODE_PRIVATE)
    }
    val lastCheck = preferences.getLong("last_check_at", 0L)
    val lastResult = preferences.getString("last_result", null)
    var manualStatus by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        SettingRow("Maintenance", "Daily database health checks and safe cleanup of abandoned temporary imports")
        SettingRow(
            "Last automatic check",
            if (lastCheck > 0L) android.text.format.DateFormat.format("MMM d, yyyy h:mm a", lastCheck).toString()
            else "Not run yet"
        )
        SettingRow("Last check result", lastResult ?: "No maintenance report available yet")
        Button(onClick = {
            val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "forgeai-maintenance-manual", ExistingWorkPolicy.KEEP, request
            )
            manualStatus = "Manual maintenance check queued."
        }) { Text("Run maintenance now") }
        if (manualStatus.isNotBlank()) {
            Text(manualStatus, color = Color(0xFF8EA6C8), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        SettingRow("Natasha personality", "Version ${NatashaPersonalityUpdates.installedVersion(context)} · natural conversation, no special prompts required")
        SettingRow("Privacy", "Local-first storage and no required account")
        SettingRow("App updates", "Install new APK releases manually; automatic app updates are not implemented")
        SettingRow("Models", "Independent from Natasha's personality and memory")
        Spacer(Modifier.height(18.dp))
        Text("Status: " + status, color = Color(0xFF8EA6C8))
    }
}

@Composable
private fun SettingRow(title: String, detail: String) {
    Surface(
        color = Color(0xFF111B2E),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFF8EA6C8))
        }
    }
}

private fun readUriTextLimited(context: Context, uri: Uri, maxBytes: Int): String {
    require(maxBytes > 0) { "Read limit must be positive" }
    val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected file")
    return input.use { stream ->
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Selected file exceeds the ${maxBytes}-byte safety limit" }
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

private const val PREFERRED_MODEL_FILE = "Qwen_Qwen3-4B-Instruct-2507-Q4_K_M.gguf"
private const val PREFERRED_MODEL_ASSET = "models/$PREFERRED_MODEL_FILE"
private const val PREFERRED_MODEL_MIN_BYTES = 2_300_000_000L
private const val PREFERRED_MODEL_MAX_BYTES = 2_800_000_000L

private suspend fun ensurePreferredModel(
    context: Context,
    db: AppDatabase,
    onStage: (String) -> Unit,
    onProgress: (Long, Long) -> Unit
): ModelEntity {
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    val expectedSize = context.assets.openFd(PREFERRED_MODEL_ASSET).use { it.length }
    require(expectedSize in PREFERRED_MODEL_MIN_BYTES..PREFERRED_MODEL_MAX_BYTES) {
        "The bundled Qwen3 4B model asset is missing or has an unexpected size."
    }
    val target = File(dir, PREFERRED_MODEL_FILE)
    if (target.exists() && target.length() != expectedSize) target.delete()

    if (!target.exists()) {
        val available = android.os.StatFs(dir.absolutePath).availableBytes
        val required = expectedSize + 512L * 1024L * 1024L
        require(available >= required) {
            "ForgeAI needs about 2.5 GB of free storage to prepare its included model. Free up storage and reopen ForgeAI."
        }
        onStage("Preparing the model included with ForgeAI…")
        val partial = File(dir, "$PREFERRED_MODEL_FILE.installing")
        try {
            context.assets.open(PREFERRED_MODEL_ASSET).buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= expectedSize) { "Bundled model asset exceeded its expected size." }
                        output.write(buffer, 0, count)
                        onProgress(copied, expectedSize)
                    }
                }
            }
            require(partial.length() == expectedSize) { "The included model could not be fully installed." }
            onStage("Verifying the included model…")
            val actualHash = sha256(partial)
            val expectedHash = context.assets.open("models/model.sha256")
                .bufferedReader().use { it.readText().trim().substringBefore(' ') }
            require(expectedHash.matches(Regex("[a-fA-F0-9]{64}")) && actualHash.equals(expectedHash, true)) {
                "The included model failed its SHA-256 integrity check."
            }
            require(partial.renameTo(target)) { "Unable to finish installing the included model." }
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    onStage("Checking the included model…")
    require(target.length() == expectedSize) { "The included model file is incomplete; reinstall ForgeAI." }
    val digest = sha256(target)
    val expectedHash = context.assets.open("models/model.sha256")
        .bufferedReader().use { it.readText().trim().substringBefore(' ') }
    require(digest.equals(expectedHash, true)) { "The included model failed its SHA-256 integrity check." }

    val existing = db.models().all().firstOrNull { it.path == target.absolutePath }
    val model = ModelEntity(
        path = target.absolutePath,
        name = "Qwen3-4B-Instruct-2507 · Q4_K_M",
        sizeBytes = target.length(),
        sha256 = digest,
        quantization = "Q4_K_M",
        selected = true
    )
    db.models().clearSelection()
    onStage("Finishing Natasha's setup…")
    db.models().save(existing?.copy(
        name = model.name,
        sizeBytes = model.sizeBytes,
        sha256 = model.sha256,
        quantization = model.quantization,
        selected = true
    ) ?: model)
    return model
}

private fun managedModelFile(context: Context, path: String): File? {
    return runCatching {
        val root = File(context.filesDir, "models").canonicalFile
        val file = File(path).canonicalFile
        if (file.path.startsWith(root.path + File.separator)) file else null
    }.getOrNull()
}

private fun copyModelIntoApp(context: Context, uri: Uri): File {
    val MODEL_STORAGE_RESERVE = 256L * 1024 * 1024
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    val rawName = queryDisplayName(context, uri) ?: "model-" + System.currentTimeMillis() + ".gguf"
    val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val modelName = if (safeName.endsWith(".gguf", ignoreCase = true)) safeName else "$safeName.gguf"
    val expectedSize = querySize(context, uri)
    require(expectedSize == null || expectedSize > 0L) { "Selected model is empty" }
    val availableBytes = android.os.StatFs(dir.absolutePath).availableBytes
    val importLimit = (availableBytes - MODEL_STORAGE_RESERVE).coerceAtLeast(0L)
    require(expectedSize == null || expectedSize <= importLimit) { "Not enough free storage; keep at least 256 MB available" }
    val base = modelName.substringBeforeLast('.')
    var target = File(dir, "$base.gguf")
    if (target.exists()) target = File(dir, "$base-${System.currentTimeMillis()}.gguf")
    val partial = File(dir, target.name + ".partial")
    try {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected model" }
            partial.outputStream().buffered().use { output ->
                val buffer = ByteArray(1024 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(copied <= importLimit) { "Not enough free storage; keep at least 256 MB available" }
                    output.write(buffer, 0, read)
                }
            }
        }
        require(partial.length() > 0L) { "Selected model is empty" }
        require(expectedSize == null || partial.length() == expectedSize) { "Model import was incomplete; the original file was kept" }
        require(partial.renameTo(target)) { "Unable to finalize imported model" }
        return target
    } finally {
        if (partial.exists()) partial.delete()
    }
}

private fun querySize(context: Context, uri: Uri): Long? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
    }
    return null
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0)
    }
    return null
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun detectQuantization(name: String): String {
    val lower = name.lowercase()
    val matches = Regex("(?i)(q[2-8]_[a-z0-9_]+|iq[1-4]_[a-z0-9_]+)").find(lower)
    return matches?.value?.uppercase() ?: "Unknown"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    return "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
