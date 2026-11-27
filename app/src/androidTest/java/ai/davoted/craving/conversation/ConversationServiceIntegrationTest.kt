package ai.davoted.craving.conversation

import ai.davoted.craving.ai.ArchiveAccessLevel
import ai.davoted.craving.ai.ArchiveAuthenticityStatus
import ai.davoted.craving.ai.ArchiveCategory
import ai.davoted.craving.ai.ArchiveContext
import ai.davoted.craving.ai.ArchiveFactClaim
import ai.davoted.craving.ai.ArchiveFactualContext
import ai.davoted.craving.ai.ArchiveSource
import ai.davoted.craving.ai.ArchiveTheme
import ai.davoted.craving.ai.ConversationMessage
import ai.davoted.craving.ai.ExperienceMood
import ai.davoted.craving.ai.ExperiencePacing
import ai.davoted.craving.ai.LlamaCppAdapter
import ai.davoted.craving.ai.LlamaCppRuntime
import ai.davoted.craving.ai.LocalModelConfiguration
import ai.davoted.craving.ai.ScenarioPhase
import ai.davoted.craving.ai.UserArchiveFraming
import ai.davoted.craving.storage.ContinuitySnapshot
import ai.davoted.craving.storage.LocalContinuityStore
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real ConversationService -> PersonaOrchestrator -> LlamaCppAdapter ->
 * PromptContextAssembler path while replacing only the native runtime boundary.
 *
 * The encrypted continuity store and Android Keystore are real. No network or GGUF is needed.
 */
@RunWith(AndroidJUnit4::class)
class ConversationServiceIntegrationTest {

    @Test
    fun factualModePassesVerifiedArchiveAndRestoredStateThroughFullGenerationCycle() = runBlocking {
        val context = targetContext()
        val runtime = RecordingRuntime()
        val store = LocalContinuityStore(context)
        val model = prepareSession(context, store)
        val service = createService(context, store, runtime)

        try {
            service.initialize()
            assertEquals(LocalModelStatus.READY, service.state.value.modelStatus)

            service.setMode(ConversationMode.FACTUAL)
            service.selectArchiveContext(verifiedArchive())
            service.sendPrompt("Summarize the selected public interview.")

            val prompt = runtime.lastPrompt
            assertTrue("The restored experience state must reach the final prompt.", prompt.contains("mood=TENDER; intensity=2; pacing=SLOW; phase=BUILD"))
            assertTrue("The verified archive claim must reach the final prompt.", prompt.contains("A source-supported public career statement. [source: transcript:12]"))
            assertTrue("The archive provenance URL must reach the final prompt.", prompt.contains("https://example.org/interview"))
            assertTrue(service.state.value.messages.any { it.role == "assistant" && it.content == GENERATED_TEXT })

            val persisted = store.load()
            assertEquals("FACTUAL", persisted.conversationMode)
            assertEquals("PLAYFUL", persisted.experienceMood)
            assertEquals(3, persisted.experienceIntensity)
            assertEquals("BRISK", persisted.experiencePacing)
            assertEquals("TURNING_POINT", persisted.experiencePhase)
        } finally {
            service.close()
            model.delete()
        }
    }

    @Test
    fun fictionalModeExcludesSelectedArchiveFactsButStillCompletesGeneration() = runBlocking {
        val context = targetContext()
        val runtime = RecordingRuntime()
        val store = LocalContinuityStore(context)
        val model = prepareSession(context, store)
        val service = createService(context, store, runtime)

        try {
            service.initialize()
            service.setMode(ConversationMode.FICTIONAL)
            service.selectArchiveContext(verifiedArchive())
            service.sendPrompt("Continue the fictional scene.")

            val prompt = runtime.lastPrompt
            assertFalse("Fictional mode must not receive archive facts.", prompt.contains("A source-supported public career statement."))
            assertFalse("Fictional mode must not receive archive provenance.", prompt.contains("https://example.org/interview"))
            assertTrue("Fictional mode must retain the normal no-archive grounding guard.", prompt.contains("No verified, source-located archive facts are available."))
            assertTrue(service.state.value.messages.any { it.role == "assistant" && it.content == GENERATED_TEXT })

            val persisted = store.load()
            assertEquals("FICTIONAL", persisted.conversationMode)
            assertEquals("PLAYFUL", persisted.experienceMood)
            assertEquals(3, persisted.experienceIntensity)
            assertEquals("TURNING_POINT", persisted.experiencePhase)
        } finally {
            service.close()
            model.delete()
        }
    }

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun prepareSession(
        context: Context,
        store: LocalContinuityStore
    ): File {
        val model = File(context.filesDir, "conversation-integration-fake.gguf")
        check(model.writeText("test-only fake model marker"))
        store.save(
            ContinuitySnapshot(
                selectedModelPath = model.absolutePath,
                conversationMode = "FICTIONAL",
                experienceMood = "TENDER",
                experienceIntensity = 2,
                experiencePacing = "SLOW",
                experiencePhase = "BUILD"
            )
        )
        return model
    }

    private fun createService(
        context: Context,
        store: LocalContinuityStore,
        runtime: RecordingRuntime
    ): ConversationService {
        val adapter = LlamaCppAdapter(
            runtime = runtime,
            configuration = LocalModelConfiguration(
                contextSize = 2048,
                threadCount = 2,
                maxTokens = 48,
                temperature = 0.0f
            )
        )
        return ConversationService(context, adapter, store)
    }

    private fun verifiedArchive() = ArchiveContext(
        archiveItemId = "integration-archive-1",
        title = "Public career interview",
        category = ArchiveCategory.INTERVIEW,
        source = ArchiveSource(
            sourceId = "integration-source-1",
            url = "https://example.org/interview",
            publisher = "Example Publisher",
            capturedAtMillis = 100L,
            accessLevel = ArchiveAccessLevel.PUBLIC,
            usageLicense = "public",
            authenticityStatus = ArchiveAuthenticityStatus.VERIFIED,
            verifiedAtMillis = 200L
        ),
        factualContext = ArchiveFactualContext(
            summary = "Verified public interview context.",
            claims = listOf(
                ArchiveFactClaim(
                    text = "A source-supported public career statement.",
                    sourceLocator = "transcript:12"
                )
            )
        ),
        userFraming = UserArchiveFraming(
            theme = ArchiveTheme.NEUTRAL,
            direction = "Discuss the public interview accurately."
        ),
        selectedAtMillis = 300L
    )

    private class RecordingRuntime : LlamaCppRuntime {
        override val isAvailable: Boolean = true
        var lastPrompt: String = ""

        override fun loadModel(modelPath: String, contextSize: Int, threadCount: Int): Boolean =
            File(modelPath).isFile && contextSize == 2048 && threadCount == 2

        override fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
            lastPrompt = prompt
            return GENERATED_TEXT + STATE_BLOCK
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }

    private companion object {
        const val GENERATED_TEXT = "Integration response."
        const val STATE_BLOCK = "<dc_state>{\"schemaVersion\":1,\"mood\":\"PLAYFUL\",\"intensity\":3,\"pacing\":\"BRISK\",\"phase\":\"TURNING_POINT\"}</dc_state>"
    }
}
