package com.forgeai.chat.testing

import android.os.Debug
import com.forgeai.chat.nativebridge.GGUFManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AcceptanceRun(
    val sequence: String, val configurationId: String, val tokensPerSecond: Double,
    val fidelityScore: Double, val elapsedMs: Long, val outputTokens: Int,
    val processPssKbAfterRun: Long, val successful: Boolean, val failure: String? = null
)

data class DeviceAcceptanceReport(
    val deviceModel: String, val baseline: List<AcceptanceRun>, val stress: List<AcceptanceRun>,
    val peakObservedPssKb: Long, val requiredThreadCount: Int, val modelPath: String
) {
    val baselineSuccessful: Int get() = baseline.count { it.successful }
    val stressSuccessful: Int get() = stress.count { it.successful }
    val baselineMeanTokensPerSecond: Double get() = baseline.filter { it.successful }.map { it.tokensPerSecond }.average().takeIf { it.isFinite() } ?: 0.0
    val baselineMeanFidelity: Double get() = baseline.filter { it.successful }.map { it.fidelityScore }.average().takeIf { it.isFinite() } ?: 0.0
    val stressMeanTokensPerSecond: Double get() = stress.filter { it.successful }.map { it.tokensPerSecond }.average().takeIf { it.isFinite() } ?: 0.0
    val stressMeanFidelity: Double get() = stress.filter { it.successful }.map { it.fidelityScore }.average().takeIf { it.isFinite() } ?: 0.0
}

/** PSS is sampled after each response, not a continuous native-memory high-water mark. */
object DeviceAcceptanceRunbook {
    const val REQUIRED_CPU_THREADS = 1
    const val REQUIRED_RESPONSES_PER_SEQUENCE = 5
    const val BASELINE_CONFIGURATION_ID = "CPU-BASELINE-T07-P095-1T"
    const val STRESS_CONFIGURATION_ID = "HV-3"

    fun requiredLoadOptions(contextSize: Int = 4096) =
        GGUFManager.LoadOptions(contextSize = contextSize, threads = REQUIRED_CPU_THREADS)

    suspend fun runCpuPhaseOne(
        manager: GGUFManager, systemPrompt: String, rater: PersonaOutputRater, modelPath: String,
        deviceModel: String = android.os.Build.MODEL, timeoutMs: Int = 180_000
    ): DeviceAcceptanceReport = withContext(Dispatchers.Default) {
        val ready = manager.state.value as? GGUFManager.State.Ready
        check(ready != null) { "Load the production GGUF before running acceptance tests" }
        check(ready.threads == REQUIRED_CPU_THREADS) {
            "CPU baseline requires exactly $REQUIRED_CPU_THREADS thread; model was loaded with ${ready.threads}"
        }
        val baselineSampler = SamplerConfig(temperature = 0.7f, topP = 0.95f, frequencyPenalty = 0.0f, presencePenalty = 0.0f)
        val stressSampler = SamplerConfigurationIds.highVarianceMatrix().first { it.first == STRESS_CONFIGURATION_ID }.second
        val suite = InferenceTestFixtures.personaSuite()
        require(suite.size >= 5) { "At least five persona fixtures are required" }
        val baselinePrompts = suite.take(5)
        val stressPrompts = suite.take(5)
        var peakPss = Debug.getPss()

        suspend fun executeSequence(label: String, configurationId: String, sampler: SamplerConfig, prompts: List<TestFixture>): List<AcceptanceRun> {
            return prompts.mapIndexed { index, fixture ->
                try {
                    val result = manager.generateChat(
                        systemPrompt = systemPrompt, userMessage = fixture.prompt,
                        temperature = sampler.temperature, topP = sampler.topP,
                        frequencyPenalty = sampler.frequencyPenalty, presencePenalty = sampler.presencePenalty,
                        maxTokens = 128, timeoutMs = timeoutMs, seed = 7000 + index,
                        configurationId = configurationId
                    )
                    val pss = Debug.getPss()
                    peakPss = maxOf(peakPss, pss)
                    if (!result.success || result.text.isBlank()) {
                        AcceptanceRun(label, configurationId, result.tokensPerSecond, 0.0, result.elapsedMs, result.outputTokens, pss, false, "${result.status}: ${result.message}")
                    } else {
                        val rating = rater.rate(configurationId, fixture.id, result.text)
                        val fidelity = PersonaFidelityScorer.summarize(listOf(rating)).fidelityScore
                        AcceptanceRun(label, configurationId, result.tokensPerSecond, fidelity, result.elapsedMs, result.outputTokens, pss, true)
                    }
                } catch (error: Exception) {
                    val pss = Debug.getPss()
                    peakPss = maxOf(peakPss, pss)
                    AcceptanceRun(label, configurationId, 0.0, 0.0, 0L, 0, pss, false, "${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }

        val baseline = executeSequence("CPU_BASELINE", BASELINE_CONFIGURATION_ID, baselineSampler, baselinePrompts)
        val stress = executeSequence("CPU_STRESS_HV3", STRESS_CONFIGURATION_ID, stressSampler, stressPrompts)
        DeviceAcceptanceReport(deviceModel, baseline, stress, peakPss, ready.threads, ready.path)
    }
}
