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

    data class HealthResult(
        val runtimeInfo: String,
        val utf8RoundTripPassed: Boolean
    )

    fun runHealthCheck(): HealthResult {
        val probe = "ForgeAI JNI ✓ — café — 日本語 — 😀"
        val returned = nativeUtf8RoundTrip(probe)
        return HealthResult(
            runtimeInfo = nativeRuntimeInfo(),
            utf8RoundTripPassed = returned == probe
        )
    }
}
