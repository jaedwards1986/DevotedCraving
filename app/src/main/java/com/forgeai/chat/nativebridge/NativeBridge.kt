package com.forgeai.chat.nativebridge

/**
 * JNI boundary for the embedded llama.cpp CPU runtime.
 * Model loading is implemented; token generation is deliberately not exposed until
 * the streaming/decode path and cancellation contract are added and tested.
 */
object NativeBridge {
    init {
        System.loadLibrary("forgeai_jni")
    }

    private external fun nativeRuntimeInfo(): String
    private external fun nativeUtf8RoundTrip(value: String): String
    private external fun nativeTokenArrayRoundTrip(tokens: IntArray): IntArray
    private external fun nativeLoadModel(path: String, contextSize: Int, threads: Int): String
    private external fun nativeUnloadModel(): Boolean
    private external fun nativeIsModelLoaded(): Boolean

    data class HealthResult(
        val runtimeInfo: String,
        val utf8RoundTripPassed: Boolean,
        val tokenArrayRoundTripPassed: Boolean
    )

    data class ModelLoadResult(
        val success: Boolean,
        val detail: String
    )

    fun runHealthCheck(): HealthResult {
        val probe = "ForgeAI JNI ✓ — café — 日本語 — 😀"
        val returned = nativeUtf8RoundTrip(probe)
        val tokenProbe = intArrayOf(1, 42, 128000, 2147483647, 0)
        val returnedTokens = nativeTokenArrayRoundTrip(tokenProbe)
        return HealthResult(
            runtimeInfo = nativeRuntimeInfo(),
            utf8RoundTripPassed = returned == probe,
            tokenArrayRoundTripPassed = tokenProbe.contentEquals(returnedTokens)
        )
    }

    fun loadModel(path: String, contextSize: Int = 4096, threads: Int): ModelLoadResult {
        val result = nativeLoadModel(path, contextSize, threads)
        val success = result.startsWith("OK|")
        return ModelLoadResult(success, result.removePrefix(if (success) "OK|" else "ERROR|"))
    }

    fun unloadModel(): Boolean = nativeUnloadModel()
    fun isModelLoaded(): Boolean = nativeIsModelLoaded()
}
