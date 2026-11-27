package ai.davoted.craving.ai

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertTrue(prompt.contains("PRIORITY 3 — DIRECTIVE"))
        assertTrue(prompt.contains("PRIORITY 4 — GROUNDING"))
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

    @Test
    fun structuredContractSeparatesDirectivesFromVerifiedGrounding() {
        val archive = AssembledArchiveExperience(
            archiveItemId = "archive-1",
            archiveFacts = listOf(VerifiedArchiveFact("Public interview fact.", "transcript:7", emptyList())),
            provenance = null,
            userFraming = UserArchiveFraming(ArchiveTheme.PLAYFUL, "Use this as fictional inspiration."),
            confirmedPreferences = emptyList(),
            exclusions = emptyList(),
            presentation = PresentationParameters(5, 4, 5, 5, 5),
            characterState = ExperienceState(),
            memoryStateId = "memory-1"
        )
        val result = assembler.assemble(
            ConversationContext(
                personaState = ExperienceState(ExperienceMood.PLAYFUL, 2, ExperiencePacing.SLOW, ScenarioPhase.BUILD),
                confirmedPreferences = listOf("cinematic pacing"),
                exclusions = listOf("no humiliation"),
                relationshipContext = listOf("Continuity note: user prefers gradual scene progression."),
                archiveExperience = archive
            ),
            listOf(ConversationMessage("user", "Continue."))
        )

        assertTrue(result.systemPrompt.indexOf("PRIORITY 1") < result.systemPrompt.indexOf("PRIORITY 2"))
        assertTrue(result.systemPrompt.contains("no humiliation"))
        assertTrue(result.systemPrompt.contains("cinematic pacing"))
        assertTrue(result.systemPrompt.contains("Continuity note"))
        assertTrue(result.instructionBlock.indexOf("PRIORITY 3") < result.instructionBlock.indexOf("PRIORITY 4"))
        assertTrue(result.instructionBlock.contains("Use this as fictional inspiration"))
        assertTrue(result.instructionBlock.contains("Public interview fact. [source: transcript:7]"))
        assertTrue(result.serializedPrompt.contains("ASSISTANT:"))
        assertEquals(64, result.contextFingerprint.length)
    }

    @Test
    fun fingerprintIsStableForEquivalentContextAndChangesWhenPromptInputChanges() {
        val base = ConversationContext(
            personaState = ExperienceState(),
            confirmedPreferences = listOf("warm tone", "short replies"),
            exclusions = listOf("no insults")
        )
        val first = assembler.assemble(base, listOf(ConversationMessage("user", "Hello")))
        val reordered = assembler.assemble(
            base.copy(confirmedPreferences = listOf("short replies", "warm tone")),
            listOf(ConversationMessage("user", "Hello"))
        )
        val changed = assembler.assemble(
            base.copy(exclusions = listOf("no insults", "no shouting")),
            listOf(ConversationMessage("user", "Hello"))
        )

        assertEquals(first.serializedPrompt, reordered.serializedPrompt)
        assertEquals(first.contextFingerprint, reordered.contextFingerprint)
        assertNotEquals(first.contextFingerprint, changed.contextFingerprint)
    }

    @Test
    fun concurrentAssemblyStressKeepsFingerprintsAndContextsIsolated() {
        val workers = 16
        val iterationsPerWorker = 32
        val executor = java.util.concurrent.Executors.newFixedThreadPool(workers)
        try {
            val baselines = (0 until workers).map { worker ->
                val token = "ISOLATION_TOKEN_WORKER_$worker"
                val context = ConversationContext(
                    personaState = ExperienceState(),
                    confirmedPreferences = listOf("preference-$worker"),
                    exclusions = listOf("exclusion-$worker"),
                    relationshipContext = listOf("continuity-$worker")
                )
                val messages = listOf(ConversationMessage("user", token))
                Triple(token, context, assembler.assemble(context, messages))
            }

            val tasks = baselines.map { (token, context, baseline) ->
                java.util.concurrent.Callable {
                    repeat(iterationsPerWorker) {
                        val result = assembler.assemble(
                            context,
                            listOf(ConversationMessage("user", token))
                        )
                        assertEquals(baseline.contextFingerprint, result.contextFingerprint)
                        assertTrue(result.serializedPrompt.contains(token))
                        assertTrue(result.serializedPrompt.contains("preference-${token.substringAfterLast("_").toInt()}"))
                        assertEquals(64, result.contextFingerprint.length)
                    }
                }
            }

            executor.invokeAll(tasks).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun conversationHistoryIsLastAndRetainsChronologicalOrder() {
        val result = assembler.assemble(
            ConversationContext(personaState = ExperienceState()),
            listOf(
                ConversationMessage("user", "First message"),
                ConversationMessage("assistant", "Second message")
            )
        )

        val first = result.serializedPrompt.indexOf("USER: First message")
        val second = result.serializedPrompt.indexOf("ASSISTANT: Second message")
        assertTrue(first >= 0 && first < second)
        assertTrue(result.instructionBlock.contains("PRIORITY 5 — HISTORY"))
    }

    @Test
    fun fictionalPromptStreamDoesNotReceiveArchiveFacts() {
        val result = assembler.assemble(
            context = ConversationContext(
                personaState = ExperienceState(),
                confirmedPreferences = listOf("cinematic pacing"),
                exclusions = listOf("no humiliation"),
                archiveExperience = null
            ),
            messages = listOf(ConversationMessage("user", "Continue the fictional scene."))
        )

        assertTrue(result.serializedPrompt.contains("No verified, source-located archive facts are available."))
        assertTrue(!result.serializedPrompt.contains("Public interview fact."))
        assertTrue(result.serializedPrompt.contains("cinematic pacing"))
    }

    @Test
    fun selectedArchiveMetadataChangesPromptAndFingerprint() {
        val base = AssembledArchiveExperience(
            archiveItemId = "local-item-a",
            archiveFacts = emptyList(),
            provenance = null,
            userFraming = null,
            confirmedPreferences = emptyList(),
            exclusions = emptyList(),
            presentation = PresentationParameters(5, 5, 5, 5, 5),
            characterState = ExperienceState(),
            memoryStateId = "memory",
            archiveTitle = "Imported image A",
            archiveCategory = ArchiveCategory.PHOTOGRAPH,
            authenticityStatus = ArchiveAuthenticityStatus.UNVERIFIED
        )
        val first = assembler.assemble(
            ConversationContext(personaState = ExperienceState(), archiveExperience = base),
            listOf(ConversationMessage("user", "Discuss this item."))
        )
        val second = assembler.assemble(
            ConversationContext(
                personaState = ExperienceState(),
                archiveExperience = base.copy(archiveTitle = "Imported image B")
            ),
            listOf(ConversationMessage("user", "Discuss this item."))
        )

        assertTrue(first.instructionBlock.contains("Imported image A"))
        assertTrue(first.contextFingerprint != second.contextFingerprint)
    }

}
