package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF090A0F)
private val SurfaceDark = Color(0xFF171923)
private val Gold = Color(0xFFD5B879)
private val Muted = Color(0xFF9A9CAA)
private val TextMain = Color(0xFFF2EEE8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexoraRoot() }
    }
}

private enum class Destination(val title: String) {
    Companion("Companion"), Archive("Archive"), Library("Library"), Settings("Settings")
}

@Composable
private fun NexoraRoot() {
    var destination by remember { mutableStateOf(Destination.Companion) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 18.dp)) {
                    Text("N E X O R A", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(destination.title, color = TextMain, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (destination) {
                        Destination.Companion -> CompanionPage()
                        Destination.Archive -> InfoPage("NATASHA NICE ARCHIVE", "A source-verified public archive", "Authentic public material only. Every entry will carry its original source, access status, and usage notes. No fabricated or unverified media.")
                        Destination.Library -> InfoPage("YOUR COLLECTION", "Saved items and favorites", "A private collection for bookmarks and authorized offline items. NEXORA never imports your phone's photos automatically.")
                        Destination.Settings -> SettingsPage()
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(SurfaceDark).padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Destination.entries.forEach { item ->
                        Text(
                            item.title,
                            color = if (destination == item) Gold else Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { destination = item }.padding(horizontal = 7.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionPage() {
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf("Your companion space is ready. Connect a local model to begin.")) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        InfoCard("YOUR COMPANION", "A fictional adult character experience", "NEXORA owns the character definition, conversation history, and memory. They remain independent of the language model you choose.")
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(messages) { message ->
                Text(message, color = TextMain, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(14.dp)).padding(14.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp).background(SurfaceDark, RoundedCornerShape(18.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("Write a message…", color = Muted, fontSize = 14.sp)
                    inner()
                }
            )
            Spacer(Modifier.width(12.dp))
            Text("SEND", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.clickable {
                if (input.isNotBlank()) {
                    messages = messages + "You: $input"
                    input = ""
                }
            }.padding(5.dp))
        }
        Text("Build preview: local AI inference is not connected yet.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun InfoPage(label: String, title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        InfoCard(label, title, description)
        Spacer(Modifier.height(28.dp))
        Text("Nothing here yet", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SettingsPage() {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        InfoCard("DEVICE SETTINGS", "Private and offline-first", "No account, ads, paid API, or remote chat service. Internet access will be optional and user-approved.")
        Spacer(Modifier.height(12.dp))
        SettingLine("Local GGUF model", "Not connected")
        SettingLine("On-device inference", "llama.cpp integration pending")
        SettingLine("Persistent memory", "App-owned storage integration pending")
        SettingLine("App launch PIN", "Security milestone pending")
        SettingLine("Target device", "Google Pixel 9 Pro XL")
    }
}

@Composable
private fun SettingLine(name: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(SurfaceDark, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Text(name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun InfoCard(label: String, title: String, description: String) {
    Column(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(18.dp)).padding(17.dp)) {
        Text(label, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(description, color = Muted, fontSize = 13.sp)
    }
}
