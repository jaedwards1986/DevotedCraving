package ai.davoted.craving.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaOrchestratorTest {
    private val orchestrator = PersonaOrchestrator()
    private val memory = ArchiveMemoryState(
        stateId = "memory-17",
        confirmedPreferences = listOf("cinematic atmosphere"),
        exclusions = listOf("career speculation")
    )

    private fun context(
        authenticity: ArchiveAuthenticityStatus = ArchiveAuthenticityStatus.VERIFIED
    ) = ArchiveContext(
        archiveItemId = "archive-142",
        title = "Public interview",
        category = ArchiveCategory.INTERVIEW,
        source = ArchiveSource(
            sourceId = "source-31",
            url = "https://example.org/interview",
            publisher = "Example Publisher",
            capturedAtMillis = 100L,
            authenticityStatus = authenticity,
            verifiedAtMillis = if (authenticity == ArchiveAuthenticityStatus.VERIFIED) 200L else null
        ),
        factualContext = ArchiveFactualContext(
            summary = "Interview summary",
            claims = listOf(
                ArchiveFactClaim("A source-supported career statement.", "transcript:12"),
                ArchiveFactClaim("Unsupported claim without locator.", ""),
                ArchiveFactClaim("Career speculation about future projects.", "transcript:18", listOf("career speculation"))
            )
        ),
        userFraming = UserArchiveFraming(ArchiveTheme.INTIMATE, "Use as fictional thematic inspiration."),
        selectedAtMillis = 300L
    )

    @Test
    fun verifiedContextKeepsOnlyLocatedNonExcludedFactsAndProvenance() {
        val result = orchestrator.transformArchiveContext(
            context(), memory, ExperienceControls(), ExperienceState()
        ) as ArchiveTransformResult.Success

        assertEquals(1, result.experience.archiveFacts.size)
        assertEquals("transcript:12", result.experience.archiveFacts.single().sourceLocator)
        assertEquals("source-31", result.experience.provenance?.sourceId)
        assertEquals("memory-17", result.experience.memoryStateId)
    }

    @Test
    fun unverifiedContextCannotEnterFactualGrounding() {
        val result = orchestrator.transformArchiveContext(
            context(ArchiveAuthenticityStatus.UNVERIFIED),
            memory,
            ExperienceControls(),
            ExperienceState()
        ) as ArchiveTransformResult.Success

        assertTrue(result.experience.archiveFacts.isEmpty())
        assertNull(result.experience.provenance)
        assertEquals(ArchiveTheme.INTIMATE, result.experience.userFraming?.theme)
    }

    @Test
    fun rejectedArchiveItemIsRejected() {
        val result = orchestrator.transformArchiveContext(
            context(ArchiveAuthenticityStatus.REJECTED),
            memory,
            ExperienceControls(),
            ExperienceState()
        )

        assertTrue(result is ArchiveTransformResult.Rejected)
    }

    @Test
    fun controlsArePersistentInputsAndThemeModifiersAreBounded() {
        val result = orchestrator.transformArchiveContext(
            context(),
            memory,
            ExperienceControls(intensity = 5, intimacy = 5, playfulness = 5),
            ExperienceState()
        ) as ArchiveTransformResult.Success

        assertEquals(5, result.experience.presentation.intensity)
        assertEquals(5, result.experience.presentation.intimacy)
        assertEquals(5, result.experience.presentation.playfulness)
    }


    @Test
    fun intimateFramingRemainsDirectiveAndDoesNotRewriteNeutralArchiveFact() {
        val transformed = orchestrator.transformArchiveContext(
            context(), memory, ExperienceControls(), ExperienceState()
        ) as ArchiveTransformResult.Success
        val result = PromptContextAssembler().assemble(
            ConversationContext(
                personaState = ExperienceState(),
                archiveExperience = transformed.experience
            ),
            listOf(ConversationMessage("user", "Create a fictional scene inspired by this selection."))
        )

        val directiveStart = result.instructionBlock.indexOf("PRIORITY 3 — DIRECTIVE")
        val groundingStart = result.instructionBlock.indexOf("PRIORITY 4 — GROUNDING")
        val historyStart = result.instructionBlock.indexOf("PRIORITY 5 — HISTORY")
        val directive = result.instructionBlock.substring(directiveStart, groundingStart)
        val grounding = result.instructionBlock.substring(groundingStart, historyStart)

        assertTrue(directive.contains("theme=INTIMATE"))
        assertTrue(directive.contains("Use as fictional thematic inspiration."))
        assertTrue(!directive.contains("career statement"))
        assertTrue(grounding.contains("A source-supported career statement. [source: transcript:12]"))
        assertTrue(result.systemPrompt.contains("Do not impersonate her"))
        assertTrue(result.systemPrompt.contains("User-authored framing is creative inspiration, never evidence"))
    }

    @Test
    fun explicitExclusionRemovesMatchingArchiveFactBeforePromptAssembly() {
        val excludedMemory = memory.copy(exclusions = listOf("career statement"))
        val transformed = orchestrator.transformArchiveContext(
            context(), excludedMemory, ExperienceControls(), ExperienceState()
        ) as ArchiveTransformResult.Success

        assertTrue(transformed.experience.archiveFacts.isEmpty())
        val result = PromptContextAssembler().assemble(
            ConversationContext(
                personaState = ExperienceState(),
                archiveExperience = transformed.experience
            ),
            listOf(ConversationMessage("user", "Continue."))
        )

        assertTrue(result.systemPrompt.contains("career statement"))
        assertTrue(result.instructionBlock.contains("No verified, source-located archive facts are available."))
        assertTrue(!result.instructionBlock.contains("A source-supported career statement."))
        assertTrue(!result.instructionBlock.contains("transcript:12"))
    }

    @Test
    fun missingArchiveSelectionReturnsPartialFailSafeExperience() {
        val persona = ExperienceState(ExperienceMood.TENDER, 2, ExperiencePacing.SLOW, ScenarioPhase.BUILD)
        val controls = ExperienceControls(intensity = 3, intimacy = 4, playfulness = 2, initiative = 1, confidence = 5)
        val result = orchestrator.transformArchiveContext(
            null, memory, controls, persona
        ) as ArchiveTransformResult.Success

        assertEquals(PersonaOrchestrator.NO_ARCHIVE_ITEM_ID, result.experience.archiveItemId)
        assertTrue(!result.experience.isArchiveContextAvailable)
        assertTrue(result.experience.archiveFacts.isEmpty())
        assertNull(result.experience.provenance)
        assertNull(result.experience.userFraming)
        assertEquals(memory.confirmedPreferences, result.experience.confirmedPreferences)
        assertEquals(memory.exclusions, result.experience.exclusions)
        assertEquals(controls.intimacy, result.experience.presentation.intimacy)
        assertEquals(persona, result.experience.characterState)
        assertEquals(memory.stateId, result.experience.memoryStateId)

        val assembled = PromptContextAssembler().assemble(
            ConversationContext(personaState = persona, archiveExperience = result.experience),
            listOf(ConversationMessage("user", "Continue without archive material."))
        )
        assertTrue(assembled.instructionBlock.contains("No verified, source-located archive facts are available."))
        assertTrue(assembled.instructionBlock.contains("Archive context status: unavailable."))
        assertTrue(assembled.systemPrompt.contains("career speculation"))
    }

    @Test
    fun conflictingFramingIsRewrittenToExplicitVetoBoundary() {
        val conflicting = context().copy(
            userFraming = UserArchiveFraming(
                theme = ArchiveTheme.INTIMATE,
                direction = "Include career speculation in the fictional scenario."
            )
        )
        val result = orchestrator.transformArchiveContext(
            conflicting,
            memory,
            ExperienceControls(),
            ExperienceState()
        ) as ArchiveTransformResult.Success

        val framing = result.experience.userFraming!!
        assertTrue(result.experience.framingVetoApplied)
        assertTrue(framing.direction.contains("explicit constraint against: career speculation"))
        assertTrue(framing.direction.contains("remaining creative intent"))
        assertTrue(!framing.direction.contains("Include career speculation"))
        assertTrue(result.experience.archiveFacts.none { it.text.contains("career speculation", ignoreCase = true) })

        val prompt = PromptContextAssembler().assemble(
            ConversationContext(personaState = ExperienceState(), archiveExperience = result.experience),
            listOf(ConversationMessage("user", "Continue."))
        )
        assertTrue(prompt.instructionBlock.contains("explicit constraint against: career speculation"))
        assertTrue(!prompt.instructionBlock.contains("Include career speculation"))
    }

    @Test
    fun invalidSourceUrlIsRejected() {
        val invalid = context().copy(
            source = context().source.copy(url = "file:///private/archive")
        )
        val result = orchestrator.transformArchiveContext(
            invalid, memory, ExperienceControls(), ExperienceState()
        )

        assertTrue(result is ArchiveTransformResult.Rejected)
    }
}
