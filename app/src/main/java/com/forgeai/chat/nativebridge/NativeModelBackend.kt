package com.forgeai.chat.nativebridge

/**
 * Injectable boundary around the single native model runtime.
 * Production delegates to JNI; JVM tests use a deterministic fake.
 */
interface NativeModelBackend {
    fun isModelLoaded(): Boolean
    fun loadModel(path: String, contextSize: Int, threads: Int): NativeBridge.ModelLoadResult
    fun tokenize(text: String, addBos: Boolean = true): IntArray?
    fun generate(
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        frequencyPenalty: Float = 0f,
        presencePenalty: Float = 0f,
        maxTokens: Int = 128,
        timeoutMs: Int = 120_000,
        seed: Int = -1,
        configurationId: String = "baseline",
        generationCallback: GenerationCallback? = null
    ): NativeBridge.GenerationResult
    fun cancelGeneration(): Boolean
    fun unloadModel(): Boolean
}

/** The only production implementation; no remote inference implementation exists. */
object JniNativeModelBackend : NativeModelBackend {
    override fun isModelLoaded() = NativeBridge.isModelLoaded()
    override fun loadModel(path: String, contextSize: Int, threads: Int) =
        NativeBridge.loadModel(path, contextSize, threads)
    override fun tokenize(text: String, addBos: Boolean) = NativeBridge.tokenize(text, addBos)
    override fun generate(
        prompt: String,
        temperature: Float,
        topP: Float,
        frequencyPenalty: Float,
        presencePenalty: Float,
        maxTokens: Int,
        timeoutMs: Int,
        seed: Int,
        configurationId: String,
        generationCallback: GenerationCallback?
    ) = NativeBridge.generate(
        prompt, temperature, topP, frequencyPenalty, presencePenalty,
        maxTokens, timeoutMs, seed, configurationId, generationCallback
    )
    override fun cancelGeneration() = NativeBridge.cancelGeneration()
    override fun unloadModel() = NativeBridge.unloadModel()
}
