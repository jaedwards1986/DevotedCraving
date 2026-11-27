package com.unboundai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF07111F)
private val Panel = Color(0xFF102131)
private val Violet = Color(0xFF8AAFB8)
private val Cyan = Color(0xFF35C9C0)
private val Muted = Color(0xFFA6BAC5)

private data class Tool(val name: String, val detail: String, val icon: ImageVector)
private val toolList = listOf(
    Tool("Start Chat", "Talk through an idea or ask anything", Icons.AutoMirrored.Filled.Chat),
    Tool("Build an App", "Turn an idea into an Android app", Icons.Filled.Dashboard),
    Tool("Design Something", "Create screens, layouts, and concepts", Icons.Filled.Draw),
    Tool("Write Code", "Create, explain, and debug code", Icons.Filled.Code)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Color(0xFF8AAFB8), background = Ink, surface = Panel)) {
                UnBoundAI()
            }
        }
    }
}

@Composable
private fun UnBoundAI() {
    var selected by remember { mutableStateOf<Tool?>(null) }
    var editing by remember { mutableStateOf(false) }
    val shortcuts = remember { mutableStateListOf("Start Chat", "Build an App", "Design Something", "Write Code") }

    if (selected == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1B2A), Ink))),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(Color(0xFF173F55), Cyan, Color(0xFF8AAFB8))), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("UnBoundAI", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("THINK FREELY. CREATE ANYTHING.", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text("Your ideas.\nWithout limits.", color = Color.White, fontSize = 36.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold)
                Text("A creative space to explore, build, design, and bring things to life.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("WHAT WOULD YOU LIKE TO DO?", color = Cyan, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                        toolList.filter { shortcuts.contains(it.name) }.forEach { tool ->
                            Row(
                                Modifier.fillMaxWidth().background(Color(0xFF142B3A), RoundedCornerShape(16.dp)).clickable { selected = tool }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(tool.icon, null, tint = Cyan, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tool.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(tool.detail, color = Muted, fontSize = 11.sp)
                                }
                                Text("›", color = Cyan, fontSize = 24.sp)
                            }
                        }
                        TextButton(onClick = { editing = true }) {
                            Icon(Icons.Filled.Tune, null, tint = Violet)
                            Spacer(Modifier.width(6.dp))
                            Text("Customize shortcuts", color = Violet)
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your workspace", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Text("RECENT", color = Muted, fontSize = 10.sp)
                }
                Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FolderOpen, null, tint = Cyan)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Your projects will live here", color = Color.White)
                            Text("Start something new and it will appear here.", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    } else {
        var prompt by remember(selected) { mutableStateOf("") }
        Column(Modifier.fillMaxSize().background(Ink).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text("UnBoundAI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(32.dp))
            Icon(selected!!.icon, null, tint = Cyan, modifier = Modifier.size(34.dp))
            Text(selected!!.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(selected!!.detail, color = Muted, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), minLines = 4, label = { Text("Tell UnBoundAI what you have in mind") })
            Button(onClick = { }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                Icon(Icons.Filled.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Continue")
            }
            Text("Workspace preview only. AI generation is not connected yet.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Your shortcuts") },
            text = {
                Column {
                    toolList.forEach { tool ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(shortcuts.contains(tool.name), onCheckedChange = { checked ->
                                if (checked && !shortcuts.contains(tool.name)) shortcuts.add(tool.name)
                                if (!checked) shortcuts.remove(tool.name)
                            })
                            Text(tool.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = false }) { Text("Done") } },
            containerColor = Panel
        )
    }
}
