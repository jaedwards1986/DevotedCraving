package ai.davoted.craving.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppAdapterContractTest {
    @Test
    fun forwardsAssembledConversationToRuntimeAndReturnsNativeOutput() = runBlocking {
        val runtime = RecordingRuntime()
        val adapter = LlamaCppAdapter(
            runtime = runtime,
            configuration = LocalModelConfiguration(
                contextSize = 2048,
                threadCount = 2,
                maxTokens = 37,
                temperature = 0.25f
            )
        )

        try {
            assertTrue(adapter.loadModel("/test/model.gguf").isSuccess)

            val result = adapter.generate(
                messages = listOf(
                    ConversationMessage("user", "CONTRACT_SENTINEL_7F3A")
                ),
                confirmedPreferences = listOf("concise responses"),
                exclusions = listOf("no insults")
            )

            assertTrue("Adapter generation should succeed: $result", result.isSuccess)
            val turn = result.getOrThrow()

            assertEquals("FAKE_RUNTIME_RESPONSE", turn.assistantText)
            assertEquals(64, turn.contextFingerprint.length)
            assertTrue(runtime.receivedPrompt.contains("CONTRACT_SENTINEL_7F3A"))
            assertTrue(runtime.receivedPrompt.contains("concise responses"))
            assertTrue(runtime.receivedPrompt.contains("no insults"))
            assertEquals(37, runtime.receivedMaxTokens)
            assertEquals(0.25f, runtime.receivedTemperature, 0.0001f)
            assertEquals(LocalModelStatus.READY, adapter.status)
        } finally {
            adapter.close()
        }

        assertTrue(runtime.closed)
        assertFalse(runtime.receivedPrompt.isBlank())
    }

    private class RecordingRuntime : LlamaCppRuntime {
        override val isAvailable: Boolean = true
        var receivedPrompt: String = ""
        var receivedMaxTokens: Int = -1
        var receivedTemperature: Float = Float.NaN
        var closed: Boolean = false

        override fun loadModel(modelPath: String, contextSize: Int, threadCount: Int): Boolean =
            modelPath == "/test/model.gguf" && contextSize == 2048 && threadCount == 2

        override fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
            receivedPrompt = prompt
            receivedMaxTokens = maxTokens
            receivedTemperature = temperature
            return "FAKE_RUNTIME_RESPONSE"
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }
    @Test
    fun restoresValidatedExperienceStateIndependentlyOfModelSelection() {
        val adapter = LlamaCppAdapter(runtime = RecordingRuntime())
        try {
            adapter.restoreExperienceState(
                mood = "PLAYFUL",
                intensity = 4,
                pacing = "BRISK",
                phase = "BUILD"
            )
            assertEquals(ExperienceMood.PLAYFUL, adapter.experienceState.mood)
            assertEquals(4, adapter.experienceState.intensity)
            assertEquals(ExperiencePacing.BRISK, adapter.experienceState.pacing)
            assertEquals(ScenarioPhase.BUILD, adapter.experienceState.phase)

            adapter.restoreExperienceState(
                mood = "NOT_A_REAL_MOOD",
                intensity = 99,
                pacing = "BRISK",
                phase = "BUILD"
            )
            assertEquals(ExperienceMood.PLAYFUL, adapter.experienceState.mood)
            assertEquals(4, adapter.experienceState.intensity)
        } finally {
            adapter.close()
        }
    }

}
