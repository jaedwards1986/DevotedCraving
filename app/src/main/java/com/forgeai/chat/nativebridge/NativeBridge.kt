package com.forgeai.chat.nativebridge

import org.json.JSONObject

/** Receives the first non-empty decoded text piece on the native generation thread. */
fun interface FirstTokenCallback {
    fun onFirstToken(tokenPiece: String)
}

/**
 * JNI boundary for the embedded llama.cpp CPU runtime.
 * Tokenization uses the vocabulary of the currently loaded GGUF model.
 */
object NativeBridge {
    init { System.loadLibrary("forgeai_jni") }

    private external fun nativeRuntimeInfo(): String
    private external fun nativeUtf8RoundTrip(value: String): String
    private external fun nativeTokenArrayRoundTrip(tokens: IntArray): IntArray
    private external fun nativeLoadModel(path: String, contextSize: Int, threads: Int): String
    private external fun nativeTokenize(text: String, addBos: Boolean): IntArray?
    private external fun nativeUnloadModel(): Boolean
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeCancelGeneration(): Boolean
    private external fun nativeGenerate(
        prompt: String, configurationId: String, temperature: Float, topP: Float, frequencyPenalty: Float,
        presencePenalty: Float, maxTokens: Int, timeoutMs: Int, seed: Int,
        firstTokenCallback: FirstTokenCallback?
    ): String

    data class HealthResult(
        val runtimeInfo: String,
        val utf8RoundTripPassed: Boolean,
        val tokenArrayRoundTripPassed: Boolean
    )

    data class ModelLoadResult(val success: Boolean, val detail: String)

    data class GenerationResult(
        val status: String,
        val configurationId: String,
        val message: String,
        val text: String,
        val promptTokens: Int,
        val outputTokens: Int,
        val elapsedMs: Long,
        val tokensPerSecond: Double,
        val reachedEndOfGeneration: Boolean,
        val tokenIds: List<Int>
    ) {
        val success: Boolean get() = status == "OK"
        val timedOut: Boolean get() = status == "TIMEOUT"
        val cancelled: Boolean get() = status == "CANCELLED"
    }

    fun runHealthCheck(): HealthResult {
        val probe = "ForgeAI JNI ✓ — café — 日本語 — 😀"
        val returned = nativeUtf8RoundTrip(probe)
        val tokenProbe = intArrayOf(1, 42, 128000, 2147483647, 0)
        val returnedTokens = nativeTokenArrayRoundTrip(tokenProbe)
        return HealthResult(nativeRuntimeInfo(), returned == probe, tokenProbe.contentEquals(returnedTokens))
    }

    fun loadModel(path: String, contextSize: Int = 4096, threads: Int): ModelLoadResult {
        val result = nativeLoadModel(path, contextSize, threads)
        val success = result.startsWith("OK|")
        return ModelLoadResult(success, result.removePrefix(if (success) "OK|" else "ERROR|"))
    }

    /** Requires a successfully loaded model; null indicates no model or tokenizer failure. */
    fun tokenize(text: String, addBos: Boolean = true): IntArray? = nativeTokenize(text, addBos)

    /**
     * Synchronous JNI call: invoke from Dispatchers.IO or another worker thread.
     * Native code enforces a hard 128-token ceiling and cooperative decode deadline.
     */
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
        firstTokenCallback: FirstTokenCallback? = null
    ): GenerationResult {
        val raw = nativeGenerate(
            prompt, configurationId, temperature, topP, frequencyPenalty, presencePenalty,
            maxTokens.coerceAtMost(128), timeoutMs, seed, firstTokenCallback
        )
        val json = JSONObject(raw)
        val ids = json.optJSONArray("token_ids")
        return GenerationResult(
            status = json.optString("status", "ERROR"),
            configurationId = json.optString("configuration_id", configurationId),
            message = json.optString("message"),
            text = json.optString("text"),
            promptTokens = json.optInt("prompt_tokens"),
            outputTokens = json.optInt("output_tokens"),
            elapsedMs = json.optLong("elapsed_ms"),
            tokensPerSecond = json.optDouble("tokens_per_sec"),
            reachedEndOfGeneration = json.optBoolean("eog"),
            tokenIds = if (ids == null) emptyList() else List(ids.length()) { ids.getInt(it) }
        )
    }

    /** Sets a lock-free cooperative abort flag; safe while nativeGenerate holds the model mutex. */
    fun cancelGeneration(): Boolean = nativeCancelGeneration()

    fun unloadModel(): Boolean = nativeUnloadModel()
    fun isModelLoaded(): Boolean = nativeIsModelLoaded()
}
