package ai.davoted.craving.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextAssemblerTest {
    private val assembler = PromptContextAssembler()

    @Test
    fun assemblesContextInPrecedenceOrderAndPreservesProvenance() {
        val experience = AssembledArchiveExperience(
            archiveItemId = "item-9",
            archiveFacts = listOf(VerifiedArchiveFact("A verified public interview.", "transcript:4", listOf("interview"))),
            provenance = ArchiveProvenance(
                archiveItemId = "item-9",
                sourceId = "source-3",
                sourceUrl = "https://example.org/source",
                publisher = "Publisher",
                capturedAtMillis = 10L,
                verifiedAtMillis = 20L,
                usageLicense = "public",
                authenticityStatus = ArchiveAuthenticityStatus.VERIFIED
            ),
            userFraming = UserArchiveFraming(ArchiveTheme.PLAYFUL, "Use as fictional inspiration."),
            confirmedPreferences = listOf("cinematic pacing"),
            exclusions = listOf("no humiliation"),
            presentation = PresentationParameters(5, 5, 4, 5, 5),
            characterState = ExperienceState(ExperienceMood.PLAYFUL, 3, ExperiencePacing.SLOW, ScenarioPhase.BUILD),
            memoryStateId = "memory-2"
        )

        val prompt = assembler.assemble(
            messages = listOf(ConversationMessage("user", "Start a new scene.")),
            currentState = experience.characterState,
            confirmedPreferences = emptyList(),
            exclusions = emptyList(),
            archiveExperience = experience
        )

        val p1 = prompt.indexOf("PRIORITY 1")
        val p2 = prompt.indexOf("PRIORITY 2")
        val p3 = prompt.indexOf("PRIORITY 3")
        val p4 = prompt.indexOf("PRIORITY 4")
        val p5 = prompt.indexOf("PRIORITY 5")
        assertTrue(p1 >= 0 && p1 < p2 && p2 < p3 && p3 < p4 && p4 < p5)
        assertTrue(prompt.contains("no humiliation"))
        assertTrue(prompt.contains("cinematic pacing"))
        assertTrue(prompt.contains("Use as fictional inspiration"))
        assertTrue(prompt.contains("transcript:4"))
        assertTrue(prompt.contains("https://example.org/source"))
        assertTrue(prompt.contains("App-owned character state"))
    }

    @Test
    fun ordinaryGenerationWorksWithoutArchiveContext() {
        val prompt = assembler.assemble(
            messages = listOf(ConversationMessage("user", "Hello.")),
            currentState = ExperienceState(),
            confirmedPreferences = listOf("concise replies"),
            exclusions = listOf("no insults")
        )

        assertTrue(prompt.contains("PRIORITY 1"))
        assertTrue(prompt.contains("no insults"))
        assertTrue(prompt.contains("PRIORITY 3 — ARCHIVE CONTEXT: none selected"))
        assertTrue(prompt.contains("ASSISTANT:"))
    }

    @Test
    fun exclusionsAndPreferencesAreMergedWithoutLosingEither() {
        val experience = AssembledArchiveExperience(
            archiveItemId = "item-10",
            archiveFacts = emptyList(),
            provenance = null,
            userFraming = null,
            confirmedPreferences = listOf("warm tone"),
            exclusions = listOf("no insults"),
            presentation = PresentationParameters(5, 5, 5, 5, 5),
            characterState = ExperienceState(),
            memoryStateId = "memory-3"
        )
        val prompt = assembler.assemble(emptyList(), ExperienceState(), listOf("short replies"), listOf("no degradation"), experience)

        assertTrue(prompt.contains("warm tone"))
        assertTrue(prompt.contains("short replies"))
        assertTrue(prompt.contains("no insults"))
        assertTrue(prompt.contains("no degradation"))
    }
}
