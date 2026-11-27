package ai.davoted.craving.conversation

import ai.davoted.craving.ai.*
import ai.davoted.craving.storage.LocalContinuityStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real ConversationService, encrypted continuity store, PersonaOrchestrator,
 * PromptContextAssembler and LlamaCppAdapter. Only the native runtime boundary is faked.
 * This validates application routing; it is not a substitute for a real-GGUF JNI smoke test.
 */
@RunWith(AndroidJUnit4::class)
class ConversationServiceIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val continuityDirectory get() = File(context.filesDir, "continuity")

    @Before fun clearPriorContinuity() { continuityDirectory.deleteRecursively() }
    @After fun removeTestContinuity() { continuityDirectory.deleteRecursively() }

    @Test
    fun factualModeRoutesVerifiedArchiveAndRestoresExperienceState() = runBlocking {
        val runtime = DeterministicRuntime()
        val adapter = testAdapter(runtime)
        assertTrue(adapter.loadModel("deterministic-test.gguf").isSuccess)
        val store = LocalContinuityStore(context)
        val service = ConversationService(context, adapter, store)
        try {
            service.initialize()
            service.selectArchiveContext(verifiedArchive())
            service.setMode(ConversationMode.FACTUAL)
            service.sendPrompt("Summarize the selected public interview.")

            val prompt = requireNotNull(runtime.lastPrompt)
            assertTrue(prompt.contains("Selected archive item: title=Public interview"))
            assertTrue(prompt.contains("A verified public career fact. [source: transcript:12]"))
            assertTrue(prompt.contains("url=https://example.org/interview"))
            assertEquals(ConversationMode.FACTUAL, service.state.value.mode)
            assertEquals("Deterministic integration response.", service.state.value.messages.last().content)

            val snapshot = store.load()
            assertEquals("FACTUAL", snapshot.conversationMode)
            assertEquals("PLAYFUL", snapshot.experienceMood)
            assertEquals(1, snapshot.experienceIntensity)
            assertEquals("BRISK", snapshot.experiencePacing)
            assertEquals("BUILD", snapshot.experiencePhase)
            assertEquals("Deterministic integration response.", snapshot.messages.last().content)
        } finally {
            service.close()
        }

        val restoredAdapter = testAdapter(DeterministicRuntime())
        val restoredService = ConversationService(context, restoredAdapter, store)
        try {
            restoredService.initialize()
            assertEquals(ConversationMode.FACTUAL, restoredService.state.value.mode)
            assertEquals(
                ExperienceState(ExperienceMood.PLAYFUL, 1, ExperiencePacing.BRISK, ScenarioPhase.BUILD),
                restoredAdapter.experienceState
            )
        } finally {
            restoredService.close()
        }
    }

    @Test
    fun fictionalModeExcludesSelectedArchiveFromFinalPrompt() = runBlocking {
        val runtime = DeterministicRuntime()
        val adapter = testAdapter(runtime)
        assertTrue(adapter.loadModel("deterministic-test.gguf").isSuccess)
        val store = LocalContinuityStore(context)
        val service = ConversationService(context, adapter, store)
        try {
            service.initialize()
            service.selectArchiveContext(verifiedArchive())
            service.setMode(ConversationMode.FICTIONAL)
            service.sendPrompt("Continue the fictional adult-character scene.")

            val prompt = requireNotNull(runtime.lastPrompt)
            assertTrue(prompt.contains("No verified, source-located archive facts are available."))
            assertTrue(prompt.contains("No archive-inspired user framing was selected."))
            assertFalse(prompt.contains("Public interview"))
            assertFalse(prompt.contains("A verified public career fact."))
            assertFalse(prompt.contains("transcript:12"))
            assertEquals(ConversationMode.FICTIONAL, service.state.value.mode)
            assertEquals("Deterministic integration response.", service.state.value.messages.last().content)

            val snapshot = store.load()
            assertEquals("FICTIONAL", snapshot.conversationMode)
            assertEquals("PLAYFUL", snapshot.experienceMood)
            assertEquals(1, snapshot.experienceIntensity)
            assertEquals("BUILD", snapshot.experiencePhase)
        } finally {
            service.close()
        }
    }

    private fun testAdapter(runtime: LlamaCppRuntime) = LlamaCppAdapter(
        runtime = runtime,
        configuration = LocalModelConfiguration(
            contextSize = 2048, threadCount = 2, maxTokens = 37, temperature = 0.0f
        )
    )

    private fun verifiedArchive() = ArchiveContext(
        archiveItemId = "integration-archive-1",
        title = "Public interview",
        category = ArchiveCategory.INTERVIEW,
        source = ArchiveSource(
            sourceId = "integration-source-1",
            url = "https://example.org/interview",
            publisher = "Example Publisher",
            capturedAtMillis = 1_000L,
            accessLevel = ArchiveAccessLevel.PUBLIC,
            usageLicense = "test-fixture",
            authenticityStatus = ArchiveAuthenticityStatus.VERIFIED,
            verifiedAtMillis = 2_000L
        ),
        factualContext = ArchiveFactualContext(
            summary = "Verified interview fixture.",
            claims = listOf(ArchiveFactClaim(
                "A verified public career fact.", "transcript:12", listOf("career")
            ))
        ),
        userFraming = UserArchiveFraming(),
        selectedAtMillis = 3_000L
    )

    private class DeterministicRuntime : LlamaCppRuntime {
        override val isAvailable = true
        @Volatile var lastPrompt: String? = null
            private set

        override fun loadModel(modelPath: String, contextSize: Int, threadCount: Int) =
            modelPath == "deterministic-test.gguf" && contextSize == 2048 && threadCount == 2

        override fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
            lastPrompt = prompt
            return """
                Deterministic integration response.
                <dc_state>{"schemaVersion":1,"mood":"PLAYFUL","intensity":4,"pacing":"BRISK","phase":"BUILD"}</dc_state>
            """.trimIndent()
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }
}
