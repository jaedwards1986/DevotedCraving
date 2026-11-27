package ai.davoted.craving.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.davoted.craving.ui.security.PinGate

private enum class PrimaryDestination(val label: String) {
    HOME("The Pleasure Palace"),
    SECRETARY("The Naughty Secretary"),
    APP_BUILDER("DesireForge"),
    DESIGN_STUDIO("KinkyCanvas"),
    CODE_STUDIO("SinSource"),
    PROJECTS("PleasurePod")
}

private enum class SecretarySection(val label: String) {
    CONVERSATION("Conversation"),
    ARCHIVE("Natasha Archive"),
    MEMORY("Memory")
}

@Composable
fun DavotedCravingApp() {
    var unlocked by rememberSaveable { mutableStateOf(false) }

    CravingTheme {
        if (unlocked) {
            UnBoundAIWorkspace()
        } else {
            PinGate(onUnlocked = { unlocked = true })
        }
    }
}

@Composable
private fun UnBoundAIWorkspace() {
    var selected by rememberSaveable { mutableStateOf(PrimaryDestination.HOME) }
    var secretarySection by rememberSaveable { mutableStateOf(SecretarySection.CONVERSATION) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(top = 12.dp)
        ) {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(
                    "UnBoundAI",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "THINK FREELY. CREATE ANYTHING. BUILD EVERYTHING.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PrimaryDestination.values().toList()) { destination ->
                    FilterChip(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        label = { Text(destination.label, maxLines = 1) }
                    )
                }
            }

            if (selected == PrimaryDestination.SECRETARY) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SecretarySection.values().toList()) { section ->
                        FilterChip(
                            selected = secretarySection == section,
                            onClick = { secretarySection = section },
                            label = { Text(section.label) }
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Keep the local chat ViewModel and native runtime alive while moving between destinations.
                LocalInferenceScreen(
                    modifier = if (
                        selected == PrimaryDestination.SECRETARY &&
                        secretarySection == SecretarySection.CONVERSATION
                    ) Modifier.fillMaxSize() else Modifier.size(0.dp)
                )

                when (selected) {
                    PrimaryDestination.HOME -> PleasurePalaceHome(
                        onSelect = { selected = it }
                    )

                    PrimaryDestination.SECRETARY -> when (secretarySection) {
                        SecretarySection.CONVERSATION -> Unit
                        SecretarySection.ARCHIVE -> ArchiveScreen(Modifier.fillMaxSize())
                        SecretarySection.MEMORY -> MemoryScreen(Modifier.fillMaxSize())
                    }

                    PrimaryDestination.APP_BUILDER -> PlannedWorkspace(
                        title = "DesireForge",
                        description = "Plan and develop Android applications from natural-language ideas."
                    )

                    PrimaryDestination.DESIGN_STUDIO -> PlannedWorkspace(
                        title = "KinkyCanvas",
                        description = "Create and refine interface designs, graphics, and visual concepts."
                    )

                    PrimaryDestination.CODE_STUDIO -> PlannedWorkspace(
                        title = "SinSource",
                        description = "Write, review, explain, and debug code in a dedicated workspace."
                    )

                    PrimaryDestination.PROJECTS -> PlannedWorkspace(
                        title = "PleasurePod",
                        description = "Your project library for organizing applications, designs, ideas, and ongoing work."
                    )
                }
            }
        }
    }
}

@Composable
private fun PleasurePalaceHome(onSelect: (PrimaryDestination) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "The Pleasure Palace",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Natasha at the center of your personal AI and creative workspace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "What do you want to create?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Choose a workspace below. Local conversation and imported GGUF models are available in The Naughty Secretary.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        items(PrimaryDestination.values().filter { it != PrimaryDestination.HOME }) { destination ->
            Card(
                onClick = { onSelect(destination) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when (destination) {
                                PrimaryDestination.SECRETARY -> "Chat, personal assistance, archive, and memory"
                                PrimaryDestination.APP_BUILDER -> "Application planning and development"
                                PrimaryDestination.DESIGN_STUDIO -> "Interface and visual design"
                                PrimaryDestination.CODE_STUDIO -> "Programming and debugging"
                                PrimaryDestination.PROJECTS -> "Saved projects and ongoing work"
                                PrimaryDestination.HOME -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Workspaces that are not connected yet are clearly marked as planned. No placeholder action will pretend to create or save work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlannedWorkspace(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Workspace foundation", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This destination is present in the approved navigation. Its creation workflow is not connected in this build yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
