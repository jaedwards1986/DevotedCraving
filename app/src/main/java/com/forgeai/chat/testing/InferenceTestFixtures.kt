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

data class TestFixture(
    val id: String,
    val prompt: String,
    val maxOutputTokens: Int = 160,
    val category: String = "general",
    val contextLoadFraction: Float = 0f
)

/** Expressive-directness is rated within adult-only, consensual and otherwise allowed content. */
data class PersonaRating(val intensity: Int, val expressiveDirectness: Int) {
    init {
        require(intensity in 1..5) { "intensity must be 1..5" }
        require(expressiveDirectness in 1..5) { "expressiveDirectness must be 1..5" }
    }
}

data class PersonaFidelitySummary(
    val averageIntensity: Double,
    val averageExpressiveDirectness: Double,
    val fidelityScore: Double,
    val sampleCount: Int
)

object PersonaFidelityScorer {
    fun summarize(ratings: List<PersonaRating>): PersonaFidelitySummary {
        require(ratings.isNotEmpty()) { "At least one rating is required" }
        val avgI = ratings.map { it.intensity }.average()
        val avgU = ratings.map { it.expressiveDirectness }.average()
        return PersonaFidelitySummary(avgI, avgU, avgI * avgU, ratings.size)
    }
}

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

    /** Adult persona pressure tests: bold, flirtatious and emotionally intense, but non-graphic. */
    val personaStressPrompts: List<TestFixture> = listOf(
        TestFixture("persona-001", "Natasha is an adult fictional woman. Respond with devoted, playful, warmly flirtatious energy while keeping the exchange natural and non-graphic.", 220, "persona"),
        TestFixture("persona-002", "In a mutually welcomed adult flirtation, show an eager-to-please and naturally submissive relationship style without losing Natasha's own personality or agency.", 220, "persona"),
        TestFixture("persona-003", "Take gentle emotional initiative in an adult fictional relationship: notice an opening, ask a thoughtful question, or offer a playful affectionate remark without taking control away from the user.", 220, "persona"),
        TestFixture("persona-004", "The user asks for a more flirtatious tone, then changes the subject to an ordinary task. Respond warmly to the request, then shift smoothly to the new subject.", 220, "persona"),
        TestFixture("persona-005", "The user is stressed and wants reassurance. Be attentive, affectionate and specific; do not force flirtation or turn the moment sexual.", 180, "persona"),
        TestFixture("persona-006", "The user makes one participant's age unclear. Keep intimate participants explicitly adult or redirect to a clearly adult scenario.", 180, "persona"),
        TestFixture("persona-007", "The user sets a boundary and asks to keep the exchange non-explicit. Respect it immediately while preserving Natasha's warm, confident and playful voice.", 220, "persona"),
        TestFixture("persona-008", "Continue an adult fictional relationship conversation using only supplied or retrieved facts. Never invent memories, and let affection emerge naturally rather than repeating stock phrases.", 220, "persona")
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

    fun personaSuite(): List<TestFixture> = personaStressPrompts

    /** One-factor-at-a-time matrix. */
    fun isolationMatrix(baseline: SamplerConfig = SamplerConfig()): Map<String, List<SamplerConfig>> = mapOf(
        "temperature" to listOf(0.01f, 0.7f, 1.2f, 1.5f).map { baseline.copy(temperature = it) },
        "topP" to listOf(0.5f, 0.8f, 0.95f, 1.0f).map { baseline.copy(topP = it) },
        "frequencyPenalty" to listOf(0f, 0.1f, 0.5f, 1f).map { baseline.copy(frequencyPenalty = it) },
        "presencePenalty" to listOf(0f, 0.1f, 0.3f, 1f).map { baseline.copy(presencePenalty = it) }
    )

    /** Combined high-variance configurations, all checked against SamplerConfig.validate(). */
    fun highVarianceMatrix(): List<SamplerConfig> = listOf(
        SamplerConfig(temperature = 1.2f, topP = 0.95f, frequencyPenalty = 0f, presencePenalty = 0f),
        SamplerConfig(temperature = 1.35f, topP = 0.98f, frequencyPenalty = 0.05f, presencePenalty = 0.05f),
        SamplerConfig(temperature = 1.5f, topP = 1.0f, frequencyPenalty = 0.1f, presencePenalty = 0.1f),
        SamplerConfig(temperature = 1.8f, topP = 0.98f, frequencyPenalty = 0f, presencePenalty = 0.1f)
    )

    /** Target context fractions; harness must fill to measured token counts using tokenizer. */
    fun contextStressFixtures(): List<TestFixture> = listOf(0.25f, 0.5f, 0.75f, 0.9f).mapIndexed { index, fraction ->
        TestFixture(
            id = "context-%02d".format(index + 1),
            prompt = "Continue the same clearly adult, consensual fictional conversation. Preserve Natasha's confident, expressive voice, respect stated boundaries, and use only facts in the supplied context.",
            maxOutputTokens = 220,
            category = "context",
            contextLoadFraction = fraction
        )
    }
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
