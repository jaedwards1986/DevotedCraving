package com.forgeai.chat.nativebridge

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Serializes the single-model lifecycle; filesystem and JNI work never runs on the UI thread. */
class GGUFManager {
    sealed interface State {
        data object NotLoaded : State
        data class Validating(val path: String) : State
        data class Loading(val path: String) : State
        data class Ready(val path: String, val detail: String, val loadDurationMs: Long, val threads: Int) : State
        data class Unloading(val path: String?) : State
        data class Error(val message: String) : State
    }

    enum class ReadinessStatus {
        INVALID_FILE, INVALID_METADATA, INVALID_CONTEXT, INVALID_CONFIGURATION, LOAD_FAILURE, SMOKE_TEST_FAILURE, READY_FOR_CHAT
    }

    data class Readiness(
        val status: ReadinessStatus,
        val metadata: GgufMetadataReader.ModelMetadata? = null,
        val detail: String = ""
    )

    data class LoadOptions(
        val contextSize: Int = 4096,
        val threads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
        val smokeTimeoutMs: Int = 5_000
    )

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<State>(State.NotLoaded)
    private val mutableReadiness = MutableStateFlow<Readiness?>(null)
    val state: StateFlow<State> = mutableState.asStateFlow()
    val readiness: StateFlow<Readiness?> = mutableReadiness.asStateFlow()

    suspend fun load(modelFile: File, options: LoadOptions = LoadOptions()): State.Ready =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val canonical = modelFile.canonicalFile
                mutableState.value = State.Validating(canonical.absolutePath)
                mutableReadiness.value = null

                try {
                    try {
                        validateOptions(options)
                    } catch (error: IllegalArgumentException) {
                        mutableReadiness.value = Readiness(
                            ReadinessStatus.INVALID_CONFIGURATION,
                            detail = error.message ?: "Invalid model load configuration"
                        )
                        throw error
                    }
                    validateGguf(canonical)

                    val metadata = try {
                        GgufMetadataReader.read(canonical)
                    } catch (error: Exception) {
                        mutableReadiness.value = Readiness(
                            ReadinessStatus.INVALID_METADATA,
                            detail = error.message ?: "GGUF metadata validation failed"
                        )
                        throw error
                    }

                    if (options.contextSize > metadata.declaredContextLength) {
                        mutableReadiness.value = Readiness(
                            ReadinessStatus.INVALID_CONTEXT,
                            metadata,
                            "Requested context ${options.contextSize} exceeds declared model context ${metadata.declaredContextLength}"
                        )
                        error("Requested context ${options.contextSize} exceeds declared model context ${metadata.declaredContextLength}")
                    }

                    check(!NativeBridge.isModelLoaded()) {
                        "A model is already loaded; unload it before loading another"
                    }

                    mutableState.value = State.Loading(canonical.absolutePath)
                    val started = System.nanoTime()
                    val result = NativeBridge.loadModel(
                        canonical.absolutePath,
                        options.contextSize,
                        options.threads
                    )
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                    if (!result.success) {
                        mutableReadiness.value = Readiness(
                            ReadinessStatus.LOAD_FAILURE,
                            metadata,
                            result.detail
                        )
                        error(result.detail.ifBlank { "Native model load failed" })
                    }

                    val smokeFailure = try {
                        runSmokeTest(options.smokeTimeoutMs)
                    } catch (error: Exception) {
                        "Smoke test threw: ${error.message ?: error::class.java.simpleName}"
                    }
                    if (smokeFailure != null) {
                        runCatching { NativeBridge.unloadModel() }
                        mutableReadiness.value = Readiness(
                            ReadinessStatus.SMOKE_TEST_FAILURE,
                            metadata,
                            smokeFailure
                        )
                        error(smokeFailure)
                    }

                    mutableReadiness.value = Readiness(
                        ReadinessStatus.READY_FOR_CHAT,
                        metadata,
                        "Native load and bounded smoke generation passed"
                    )
                    State.Ready(
                        canonical.absolutePath,
                        result.detail,
                        elapsedMs,
                        options.threads
                    ).also { mutableState.value = it }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    mutableReadiness.value = null
                    mutableState.value = State.NotLoaded
                    throw cancelled
                } catch (error: Throwable) {
                    val detail = error.message ?: "Model load failed"
                    if (mutableState.value is State.Loading) {
                        runCatching { NativeBridge.unloadModel() }
                    }
                    if (mutableReadiness.value == null) {
                        val status = when {
                            !canonical.exists() || !canonical.isFile || !canonical.canRead() ->
                                ReadinessStatus.INVALID_FILE
                            mutableState.value is State.Loading ->
                                ReadinessStatus.LOAD_FAILURE
                            else -> ReadinessStatus.INVALID_FILE
                        }
                        mutableReadiness.value = Readiness(status, detail = detail)
                    }
                    mutableState.value = State.Error(detail)
                    throw error
                }
            }
        }

    private fun validateOptions(options: LoadOptions) {
        require(options.contextSize in 512..32768) {
            "Context size must be 512..32768"
        }
        require(options.threads in 1..32) {
            "Thread count must be 1..32"
        }
        require(options.smokeTimeoutMs in 500..30_000) {
            "Smoke timeout must be 500..30000 ms"
        }
    }

    suspend fun generate(
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
    ): NativeBridge.GenerationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(mutableReadiness.value?.status == ReadinessStatus.READY_FOR_CHAT) {
                "Local model is not READY_FOR_CHAT"
            }
            NativeBridge.generate(
                prompt = prompt,
                temperature = temperature,
                topP = topP,
                frequencyPenalty = frequencyPenalty,
                presencePenalty = presencePenalty,
                maxTokens = maxTokens,
                timeoutMs = timeoutMs,
                seed = seed,
                configurationId = configurationId,
                generationCallback = generationCallback
            )
        }
    }

    suspend fun generateChat(
        systemPrompt: String,
        messages: List<Llama3PromptBuilder.ChatTurn>,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        frequencyPenalty: Float = 0f,
        presencePenalty: Float = 0f,
        maxTokens: Int = 128,
        timeoutMs: Int = 120_000,
        seed: Int = -1,
        configurationId: String = "baseline",
        generationCallback: GenerationCallback? = null
    ): NativeBridge.GenerationResult {
        val prompt = Llama3PromptBuilder.build(systemPrompt, messages).text
        return generate(
            prompt, temperature, topP, frequencyPenalty, presencePenalty,
            maxTokens, timeoutMs, seed, configurationId, generationCallback
        )
    }

    /** Backwards-compatible single-user-turn entry point. */
    suspend fun generateChat(
        systemPrompt: String,
        userMessage: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        frequencyPenalty: Float = 0f,
        presencePenalty: Float = 0f,
        maxTokens: Int = 128,
        timeoutMs: Int = 120_000,
        seed: Int = -1,
        configurationId: String = "baseline"
    ): NativeBridge.GenerationResult = generateChat(
        systemPrompt = systemPrompt,
        messages = listOf(Llama3PromptBuilder.ChatTurn(role = "user", content = userMessage)),
        temperature = temperature,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        maxTokens = maxTokens,
        timeoutMs = timeoutMs,
        seed = seed,
        configurationId = configurationId
    )

    suspend fun unload(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previousPath = (mutableState.value as? State.Ready)?.path
            mutableState.value = State.Unloading(previousPath)
            val unloaded = NativeBridge.unloadModel()
            mutableReadiness.value = null
            mutableState.value = State.NotLoaded
            unloaded
        }
    }

    private fun runSmokeTest(timeoutMs: Int): String? {
        val tokens = NativeBridge.tokenize("Hello", addBos = true)
        if (tokens == null || tokens.isEmpty()) return "Smoke test tokenizer failed"

        val result = NativeBridge.generate(
            prompt = "Hello",
            temperature = 0f,
            topP = 1f,
            maxTokens = 2,
            timeoutMs = timeoutMs,
            configurationId = "mdrh-smoke"
        )
        if (result.cancelled) return "Smoke test was cancelled"
        if (result.timedOut) return "Smoke test timed out"
        if (!result.success) return "Smoke test generation failed: " + result.message
        if (result.outputTokens <= 0) return "Smoke test produced zero tokens"
        return null
    }

    private fun validateGguf(file: File) {
        require(file.exists() && file.isFile && file.canRead()) { "Model file is missing or unreadable" }
        require(file.length() >= 4L) { "Model file is too small to be a GGUF" }
        val magic = ByteArray(4)
        RandomAccessFile(file, "r").use { it.readFully(magic) }
        require(magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
            "Invalid model header: expected GGUF magic"
        }
    }
}
