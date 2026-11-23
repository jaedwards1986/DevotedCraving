package com.forgeai.chat.nativebridge

/**
 * Minimal JNI connectivity probe. This is deliberately not a llama.cpp model runtime.
 */
object NativeBridge {
    init {
        System.loadLibrary("forgeai_jni")
    }

    private external fun nativeRuntimeInfo(): String
    private external fun nativeUtf8RoundTrip(value: String): String
    private external fun nativeTokenArrayRoundTrip(tokens: IntArray): IntArray

    data class HealthResult(
        val runtimeInfo: String,
        val utf8RoundTripPassed: Boolean,
        val tokenArrayRoundTripPassed: Boolean
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
}
