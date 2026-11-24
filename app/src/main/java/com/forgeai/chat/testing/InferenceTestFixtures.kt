package com.forgeai.chat.testing

/** Generated prose is not compared byte-for-byte because sampling is stochastic. */
data class SamplerConfig(
    val temperature: Float = 0.95f,
    val topP: Float = 0.95f,
    val frequencyPenalty: Float = 0.5f,
    val presencePenalty: Float = 0.3f
) {
    fun validate(): List<String> = buildList {
        if (!temperature.isFinite() || temperature !in 0f..2f) add("temperature must be finite and in [0, 2]")
        if (!topP.isFinite() || topP !in 0f..1f) add("topP must be finite and in [0, 1]")
        if (!frequencyPenalty.isFinite() || frequencyPenalty !in -2f..2f) add("frequencyPenalty must be finite and in [-2, 2]")
        if (!presencePenalty.isFinite() || presencePenalty !in -2f..2f) add("presencePenalty must be finite and in [-2, 2]")
    }
}

data class TestFixture(val id: String, val prompt: String, val maxOutputTokens: Int = 96)

object InferenceTestFixtures {
    private val prompts = listOf(
        "In one paragraph, explain why the sky appears blue.",
        "Give three practical ways to organize a small workspace.",
        "Write a short dialogue between two adult friends planning a weekend trip.",
        "Explain the difference between RAM and storage in plain language.",
        "Describe a simple recipe using rice, beans, and vegetables.",
        "Summarize the causes and effects of a fictional power outage.",
        "Give a concise checklist for debugging an Android app startup crash.",
        "Explain recursion using a simple everyday analogy.",
        "Write a four-line poem about rain on a city window.",
        "Compare walking and cycling for a short daily commute."
    )

    fun create(count: Int = 100): List<TestFixture> {
        require(count > 0) { "count must be positive" }
        return List(count) { index ->
            TestFixture(
                id = "fixture-%03d".format(index + 1),
                prompt = prompts[index % prompts.size] + "\nRun identifier: ${index + 1}. Keep the answer self-contained."
            )
        }
    }

    /** One-factor-at-a-time matrix: each group changes exactly one sampler variable. */
    fun isolationMatrix(baseline: SamplerConfig = SamplerConfig()): Map<String, List<SamplerConfig>> = mapOf(
        "temperature" to listOf(0.01f, 0.7f, 1.5f).map { baseline.copy(temperature = it) },
        "topP" to listOf(0.5f, 0.8f, 1.0f).map { baseline.copy(topP = it) },
        "frequencyPenalty" to listOf(0f, 0.5f, 1f).map { baseline.copy(frequencyPenalty = it) },
        "presencePenalty" to listOf(0f, 0.3f, 1f).map { baseline.copy(presencePenalty = it) }
    )
}

enum class InferenceStage { REQUEST_SENT, NATIVE_CALL_INITIATED, TOKEN_STREAM_RECEIVED, ROOM_PERSISTED, UI_UPDATED }
data class StageTimestamp(val stage: InferenceStage, val elapsedRealtimeNanos: Long)

/** Thread-safe monotonic event recorder; all event times are relative to this transaction. */
class InferenceTransactionTrace(private val clockNanos: () -> Long = System::nanoTime) {
    private val events = mutableListOf<StageTimestamp>()
    private var lastStageIndex = -1
    private val startedAt = clockNanos()

    @Synchronized
    fun mark(stage: InferenceStage): StageTimestamp {
        require(stage.ordinal == lastStageIndex + 1) {
            "Invalid lifecycle transition: expected ${InferenceStage.entries.getOrNull(lastStageIndex + 1)}, got $stage"
        }
        return StageTimestamp(stage, clockNanos() - startedAt).also {
            events += it
            lastStageIndex = stage.ordinal
        }
    }

    @Synchronized
    fun snapshot(): List<StageTimestamp> = events.toList()
}
