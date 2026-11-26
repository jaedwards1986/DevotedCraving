package com.forgeai.chat.nativebridge

/**
 * Stable, injectable contract for the single local inference runtime.
 * Implementations translate runtime-specific failures into NativeEngineException.
 */
enum class NativeEngineErrorCode {
    INVALID_CONFIGURATION, FILE_MISSING, FILE_CORRUPT, METADATA_INVALID,
    CONTEXT_UNSUPPORTED, NATIVE_LOAD_FAILED, NATIVE_STATE_UNKNOWN,
    SMOKE_TEST_FAILED, SMOKE_TEST_TIMEOUT, SMOKE_TEST_CANCELLED
}

open class NativeEngineException(
    val code: NativeEngineErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class NativeLoadException(message: String, cause: Throwable? = null) :
    NativeEngineException(NativeEngineErrorCode.NATIVE_LOAD_FAILED, message, cause)

class NativeStateException(message: String, cause: Throwable? = null) :
    NativeEngineException(NativeEngineErrorCode.NATIVE_STATE_UNKNOWN, message, cause)

class SmokeTestException(
    code: NativeEngineErrorCode = NativeEngineErrorCode.SMOKE_TEST_FAILED,
    message: String,
    cause: Throwable? = null
) : NativeEngineException(code, message, cause)

data class NativeModelStatus(val isLoaded: Boolean)

/**
 * Injectable boundary around the native model runtime. Filesystem/metadata
 * validation and MDRH readiness policy remain owned by GGUFManager.
 */
interface NativeModelBackend {
    fun getModelStatus(): NativeModelStatus
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

/** Production adapter: JNI-specific return codes are normalized at this boundary. */
object JniNativeModelBackend : NativeModelBackend {
    override fun getModelStatus(): NativeModelStatus = try {
        NativeModelStatus(NativeBridge.isModelLoaded())
    } catch (error: Exception) {
        throw NativeStateException("Unable to inspect native model state", error)
    }

    override fun loadModel(path: String, contextSize: Int, threads: Int): NativeBridge.ModelLoadResult {
        val result = try {
            NativeBridge.loadModel(path, contextSize, threads)
        } catch (error: Exception) {
            throw NativeLoadException("Native model load threw: ${error.message}", error)
        }
        if (!result.success) throw NativeLoadException(result.detail.ifBlank { "Native model load failed" })
        return result
    }

    override fun tokenize(text: String, addBos: Boolean) = NativeBridge.tokenize(text, addBos)

    override fun generate(
        prompt: String, temperature: Float, topP: Float, frequencyPenalty: Float,
        presencePenalty: Float, maxTokens: Int, timeoutMs: Int, seed: Int,
        configurationId: String, generationCallback: GenerationCallback?
    ) = NativeBridge.generate(
        prompt, temperature, topP, frequencyPenalty, presencePenalty,
        maxTokens, timeoutMs, seed, configurationId, generationCallback
    )

    override fun cancelGeneration() = NativeBridge.cancelGeneration()
    override fun unloadModel() = NativeBridge.unloadModel()
}
