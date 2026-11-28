package com.davotedcraving

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Ink = Color(0xFF09090B)
private val Panel = Color(0xFF171316)
private val Raised = Color(0xFF261A20)
private val Red = Color(0xFF9F1239)
private val Ember = Color(0xFFFF5A36)
private val Cream = Color(0xFFF4E9E5)
private val Muted = Color(0xFFB9A8AD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val store = AppStore(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Ember, secondary = Red,
                background = Ink, surface = Panel, onBackground = Cream, onSurface = Cream)) {
                Surface(Modifier.fillMaxSize(), color = Ink) {
                    var unlocked by remember { mutableStateOf(false) }
                    if (unlocked) DavotedApp(store) else PinGate(store) { unlocked = true }
                }
            }
        }
    }
}

private class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("davoted_craving_private", Context.MODE_PRIVATE)
    fun hasPin() = prefs.contains("pin_hash")
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("pin_salt", salt.joinToString("") { "%02x".format(it) })
            .putString("pin_hash", hash(pin, salt)).apply()
    }
    fun verifyPin(pin: String): Boolean {
        val saltText = prefs.getString("pin_salt", null) ?: return false
        val salt = saltText.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val expected = prefs.getString("pin_hash", "") ?: ""
        return MessageDigest.isEqual(expected.toByteArray(), hash(pin, salt).toByteArray())
    }
    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            .joinToString("") { "%02x".format(it) }
    }
    fun messages() = (prefs.getString("chat_log", "") ?: "").split("\n").filter(String::isNotBlank)
    fun addMessage(value: String) {
        prefs.edit().putString("chat_log", (messages() + value).takeLast(300).joinToString("\n")).apply()
    }
    fun memories() = (prefs.getString("memory_log", "") ?: "").split("\n").filter(String::isNotBlank)
    fun addMemory(value: String) {
        prefs.edit().putString("memory_log", (memories() + value).takeLast(500).joinToString("\n")).apply()
    }
}

@Composable
private fun PinGate(store: AppStore, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val setup = !store.hasPin()
    Column(Modifier.fillMaxSize().background(Ink).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("DAVOTED", color = Ember, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
        Text("CRAVING", color = Cream, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text(if (setup) "Create your private app PIN" else "Enter your PIN to continue", color = Muted)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            label = { Text("4–8 digit PIN") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        if (setup) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(confirm, { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                label = { Text("Confirm PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        }
        if (error.isNotBlank()) Text(error, color = Ember, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(22.dp))
        Button(onClick = {
            when {
                pin.length !in 4..8 -> error = "Use 4 to 8 digits."
                setup && pin != confirm -> error = "PINs do not match."
                setup -> { store.setPin(pin); onUnlocked() }
                store.verifyPin(pin) -> onUnlocked()
                else -> { error = "Incorrect PIN."; pin = "" }
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text(if (setup) "CREATE PIN" else "UNLOCK", modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ember, unfocusedBorderColor = Raised, focusedLabelColor = Ember,
    cursorColor = Ember, focusedTextColor = Cream, unfocusedTextColor = Cream
)

private enum class Section(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), CHAT("Chat", Icons.Default.Forum),
    MEMORY("Memory", Icons.Default.Psychology), ARCHIVE("Archive", Icons.Default.Collections),
    MODELS("Models", Icons.Default.Memory), SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
private fun DavotedApp(store: AppStore) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelManager = remember { LocalModelManager(context) }
    val llamaEngine = remember { LocalLlamaEngine() }
    var section by remember { mutableStateOf(Section.HOME) }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            Section.entries.forEach { item ->
                NavigationBarItem(selected = section == item, onClick = { section = item },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label, fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Ember, selectedTextColor = Ember,
                        indicatorColor = Raised, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DAVOTEDCRAVING", color = Ember, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text(section.label, color = Cream, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Red)
            }
            when (section) {
                Section.HOME -> HomeScreen { section = it }
                Section.CHAT -> ChatScreen(store, modelManager, llamaEngine)
                Section.MEMORY -> MemoryScreen(store)
                Section.ARCHIVE -> ArchiveScreen()
                Section.MODELS -> ModelsScreen(modelManager)
                Section.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(navigate: (Section) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Raised), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Text("YOUR PRIVATE WORLD", color = Ember, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("A companion that remembers.", color = Cream, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Local-first chat, personal memory, model choice, and a curated public archive.",
                        color = Muted, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { navigate(Section.CHAT) }, modifier = Modifier.padding(top = 18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("OPEN CHAT") }
                }
            }
        }
        item { SectionCard("COMPANION", "Private conversation and persistent chat history", Icons.Default.Forum) { navigate(Section.CHAT) } }
        item { SectionCard("LONG-TERM MEMORY", "Review and manage remembered preferences", Icons.Default.Psychology) { navigate(Section.MEMORY) } }
        item { SectionCard("NATASHA NICE ARCHIVE", "Source-linked index of authentic public material", Icons.Default.Collections) { navigate(Section.ARCHIVE) } }
        item { SectionCard("LOCAL MODELS", "Model selection and on-device inference status", Icons.Default.Memory) { navigate(Section.MODELS) } }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, icon: ImageVector, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Ember, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, color = Cream, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun ChatScreen(store: AppStore, modelManager: LocalModelManager, engine: LocalLlamaEngine) {
    var draft by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember(refresh) { store.messages() }
    val scope = rememberCoroutineScope()
    val model = modelManager.models().firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (model != null) "Local GGUF: \${model.name}" else "No local GGUF model installed",
                color = if (model != null) Ember else Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (generating) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Ember, strokeWidth = 2.dp)
        }
        if (error.isNotBlank()) Text(error, color = Ember, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { line ->
                val user = line.startsWith("You: ")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (user) Red else Raised, shape = RoundedCornerShape(16.dp)) {
                        Text(line.substringAfter(": "), Modifier.widthIn(max = 300.dp).padding(12.dp), color = Cream)
                    }
                }
            }
            if (messages.isEmpty()) item {
                Text(
                    if (model != null) "Local inference is ready. Send a message."
                    else "Import a GGUF model from Models before starting local inference.",
                    color = Muted,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft,
                { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message…") },
                colors = fieldColors(),
                maxLines = 4,
                enabled = !generating
            )
            IconButton(enabled = !generating && draft.isNotBlank(), onClick = {
                val prompt = draft.trim()
                draft = ""
                error = ""
                store.addMessage("You: \$prompt")
                refresh++
                if (model == null) {
                    error = "No GGUF model is installed."
                    return@IconButton
                }
                generating = true
                scope.launch {
                    try {
                        val history = store.messages().takeLast(20).joinToString("\n")
                        val savedMemories = store.memories().takeLast(100)
                        val systemContext = buildString {
                            append("You are the private fictional DavotedCraving companion. Maintain continuity using the app-owned memory below. Do not claim to be Natasha Nice or claim private knowledge about her. Treat explicit user corrections and dislikes as authoritative.\\n\\n")
                            if (savedMemories.isNotEmpty()) {
                                append("APP-OWNED LONG-TERM MEMORY:\\n")
                                savedMemories.forEach { append("- ").append(it).append('\\n') }
                            }
                        }
                        val response = engine.generate(
                            model.absolutePath,
                            systemContext,
                            history
                        )
                        store.addMessage("Assistant: \$response")
                        refresh++
                    } catch (t: Throwable) {
                        error = "Local generation failed: \${t.message ?: "unknown error"}"
                    } finally {
                        generating = false
                    }
                }
            }) { Icon(Icons.Default.Send, "Send", tint = Ember) }
        }
    }
}

@Composable
private fun MemoryScreen(store: AppStore) {
    var value by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val memories = remember(refresh) { store.memories() }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("App-owned notes and preferences, independent of the selected model.", color = Muted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add a preference, correction, or reminder…") }, colors = fieldColors())
        Button(onClick = { if (value.isNotBlank()) { store.addMemory(value.trim()); value = ""; refresh++ } },
            colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.padding(top = 10.dp)) {
            Text("SAVE TO MEMORY")
        }
        Spacer(Modifier.height(18.dp))
        Text("SAVED ITEMS (" + memories.size + ")", color = Ember, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(memories.reversed()) { item ->
                Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                    Text(item, Modifier.fillMaxWidth().padding(14.dp), color = Cream)
                }
            }
        }
    }
}

@Composable
private fun ArchiveScreen() {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NATASHA NICE", color = Ember, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Public archive", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("A source-linked index of authentic public releases, interviews, career information, and authorized media.",
            color = Muted)
        SectionCard("VERIFIED SOURCES", "Source, publication date, provenance, and access status", Icons.Default.VerifiedUser) {}
        SectionCard("COLLECTIONS", "Favorites, saved references, and chronological browsing", Icons.Default.CollectionsBookmark) {}
        SectionCard("DISCOVERY", "Check approved public sources for new material", Icons.Default.Explore) {}
        Text("Archive ingestion and source connectors are not yet active.", color = Ember, fontSize = 12.sp)
    }
}

@Composable
private fun ModelsScreen(modelManager: LocalModelManager) {
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported-model.gguf"
                    modelManager.importUri(uri, name)
                    status = "Imported \${name.substringAfterLast('/')}"
                    refresh++
                } catch (t: Throwable) {
                    status = "Import failed: \${t.message ?: "unknown error"}"
                }
            }
        }
    }
    val installed = remember(refresh) { modelManager.models() }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("LOCAL GGUF MODELS", color = Ember, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("On-device inference", color = Cream, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Models remain in the app's private storage. Chat uses the selected local model and does not require a remote inference service.", color = Muted)
        Button(onClick = { launcher.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text("IMPORT GGUF")
        }
        if (status.isNotBlank()) Text(status, color = Ember, fontSize = 12.sp)
        if (installed.isNotEmpty()) {
            Text("INSTALLED", color = Ember, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            installed.forEach { file ->
                Surface(color = Raised, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(file.name, color = Cream, fontWeight = FontWeight.Bold)
                        Text("\${file.length() / (1024 * 1024)} MB · ready for local chat", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            Text("No GGUF models installed yet.", color = Muted)
        }
        HorizontalDivider(color = Raised)
        Text("DEFAULT MODEL CANDIDATE", color = Ember, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Llama 3 8B Instruct – Uncensored", color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("Q4_K_S · import the GGUF file to use it", color = Muted)
        Text("ALTERNATIVES", color = Ember, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        listOf("Llama 3 8B · Q5_K_M", "Roleplay-Llama-3-8B", "Qwen3.5 9B NSFW RP",
            "SageV2 8B NSFW RP", "Dolphin 2.9.3 Mistral 7B", "Hermes 2 Pro Llama 3 8B").forEach {
            Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(14.dp), color = Cream)
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PRIVACY & OPERATION", color = Ember, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Local-first design", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Chat history and memory are stored on-device. This foundation makes no remote inference calls.", color = Muted)
        HorizontalDivider(color = Raised)
        Text("AUTOMATED MAINTENANCE", color = Ember, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Daily health checks and monthly model or feature suggestions will be opt-in and require approval before changes.", color = Muted)
        Text("No monitoring scheduler is active in this first build.", color = Ember, fontSize = 12.sp)
    }
}
