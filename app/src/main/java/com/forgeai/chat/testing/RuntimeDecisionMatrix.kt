package com.forgeai.chat.testing

import com.forgeai.chat.nativebridge.GGUFManager
import com.forgeai.chat.nativebridge.NativeBridge

data class RuntimeObservation(val backend: RuntimeBackend, val configurationId: String, val tokensPerSecond: Double, val fidelityScore: Double, val elapsedMs: Long, val outputTokens: Int, val successful: Boolean) {
    init {
        require(configurationId.isNotBlank())
        require(tokensPerSecond.isFinite() && tokensPerSecond >= 0.0)
        require(fidelityScore.isFinite() && fidelityScore in 0.0..25.0)
        require(elapsedMs >= 0 && outputTokens >= 0)
    }
}
enum class RuntimeBackend { CPU, VULKAN }
data class BackendComplexity(val backend: RuntimeBackend, val complexityScore: Double, val failureRate: Double) {
    init { require(complexityScore in 1.0..5.0); require(failureRate in 0.0..1.0) }
}
data class DecisionWeights(val fidelity: Double = 0.65, val speed: Double = 0.25, val complexity: Double = 0.10) {
    init {
        require(fidelity >= speed && fidelity >= complexity)
        require(speed >= 0.0 && complexity >= 0.0)
        require(kotlin.math.abs(fidelity + speed + complexity - 1.0) < 1e-9)
    }
}
data class BackendScore(val backend: RuntimeBackend, val meanFidelity: Double, val meanTokensPerSecond: Double, val complexityScore: Double, val failureRate: Double, val totalScore: Double, val observationCount: Int)
data class RuntimeDecision(val selected: RuntimeBackend, val scores: List<BackendScore>, val reason: String)

object RuntimeDecisionScorer {
    fun score(observations: List<RuntimeObservation>, complexity: List<BackendComplexity>, weights: DecisionWeights = DecisionWeights(), tieTolerance: Double = 0.05): RuntimeDecision {
        require(observations.isNotEmpty()) { "No runtime observations supplied" }
        require(tieTolerance in 0.0..1.0)
        val complexities = complexity.associateBy { it.backend }
        val grouped = observations.groupBy { it.backend }
        require(grouped.keys.all { complexities.containsKey(it) }) { "Every measured backend needs a complexity and failure-rate assessment" }
        val maxSpeed = observations.filter { it.successful }.maxOfOrNull { it.tokensPerSecond } ?: 0.0
        val scores = grouped.map { (backend, runs) ->
            val successful = runs.filter { it.successful }
            val fidelity = if (successful.isEmpty()) 0.0 else successful.map { it.fidelityScore }.average()
            val speed = if (successful.isEmpty()) 0.0 else successful.map { it.tokensPerSecond }.average()
            val c = complexities.getValue(backend)
            val speedNormalized = if (maxSpeed <= 0.0) 0.0 else speed / maxSpeed
            val complexityNormalized = (c.complexityScore - 1.0) / 4.0
            val failureRate = maxOf(c.failureRate, runs.count { !it.successful }.toDouble() / runs.size)
            val total = weights.fidelity * (fidelity / 25.0) + weights.speed * speedNormalized - weights.complexity * (complexityNormalized + failureRate)
            BackendScore(backend, fidelity, speed, c.complexityScore, failureRate, total, runs.size)
        }.sortedByDescending { it.totalScore }
        val cpu = scores.firstOrNull { it.backend == RuntimeBackend.CPU }
        val best = scores.first()
        if (cpu != null && best.backend != RuntimeBackend.CPU && best.totalScore - cpu.totalScore <= kotlin.math.abs(cpu.totalScore) * tieTolerance) {
            return RuntimeDecision(RuntimeBackend.CPU, scores, "Scores are within 5%; CPU wins the robustness tie-break.")
        }
        return RuntimeDecision(best.backend, scores, "Selected the highest weighted score from measured observations.")
    }
}

fun interface PersonaOutputRater {
    suspend fun rate(configurationId: String, promptId: String, output: String): PersonaRating
}
data class CpuMatrixReport(val observations: List<RuntimeObservation>, val failures: List<String>) {
    val successfulCount: Int get() = observations.count { it.successful }
    val failedCount: Int get() = observations.size - successfulCount
}

/** Runs ten responses per HV configuration on the loaded CPU model. Fidelity requires human ratings. */
object CpuMatrixRunner {
    suspend fun run(manager: GGUFManager, systemPrompt: String, rater: PersonaOutputRater, repeatsPerConfiguration: Int = 10, timeoutMs: Int = 120_000): CpuMatrixReport {
        require(repeatsPerConfiguration in 1..10)
        val observations = mutableListOf<RuntimeObservation>()
        val failures = mutableListOf<String>()
        val prompts = InferenceTestFixtures.personaSuite()
        SamplerConfigurationIds.highVarianceMatrix().forEach { (id, sampler) ->
            repeat(repeatsPerConfiguration) { index ->
                val fixture = prompts[index % prompts.size]
                try {
                    val result = manager.generateChat(
                        systemPrompt = systemPrompt, userMessage = fixture.prompt,
                        temperature = sampler.temperature, topP = sampler.topP,
                        frequencyPenalty = sampler.frequencyPenalty, presencePenalty = sampler.presencePenalty,
                        maxTokens = 128, timeoutMs = timeoutMs, seed = 1000 + index, configurationId = id
                    )
                    if (!result.success || result.text.isBlank()) {
                        failures += "$id/${fixture.id}/$index: ${result.status}: ${result.message}"
                        observations += RuntimeObservation(RuntimeBackend.CPU, id, result.tokensPerSecond.coerceAtLeast(0.0), 0.0, result.elapsedMs, result.outputTokens, false)
                    } else {
                        val rating = rater.rate(id, fixture.id, result.text)
                        val fidelity = PersonaFidelityScorer.summarize(listOf(rating)).fidelityScore
                        observations += RuntimeObservation(RuntimeBackend.CPU, id, result.tokensPerSecond, fidelity, result.elapsedMs, result.outputTokens, true)
                    }
                } catch (error: Exception) {
                    failures += "$id/${fixture.id}/$index: ${error.javaClass.simpleName}: ${error.message}"
                    observations += RuntimeObservation(RuntimeBackend.CPU, id, 0.0, 0.0, 0, 0, false)
                }
            }
        }
        return CpuMatrixReport(observations, failures)
    }
}
