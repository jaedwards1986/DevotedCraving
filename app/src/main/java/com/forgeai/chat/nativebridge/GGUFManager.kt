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

    data class LoadOptions(
        val contextSize: Int = 4096,
        val threads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
    )

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<State>(State.NotLoaded)
    val state: StateFlow<State> = mutableState.asStateFlow()

    suspend fun load(modelFile: File, options: LoadOptions = LoadOptions()): State.Ready =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val canonical = modelFile.canonicalFile
                mutableState.value = State.Validating(canonical.absolutePath)
                validateGguf(canonical)
                require(options.contextSize in 512..32768) { "Context size must be 512..32768" }
                require(options.threads in 1..32) { "Thread count must be 1..32" }
                check(!NativeBridge.isModelLoaded()) { "A model is already loaded; unload it before loading another" }

                mutableState.value = State.Loading(canonical.absolutePath)
                val started = System.nanoTime()
                val result = NativeBridge.loadModel(canonical.absolutePath, options.contextSize, options.threads)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                if (!result.success) {
                    mutableState.value = State.Error(result.detail)
                    error(result.detail)
                }
                State.Ready(canonical.absolutePath, result.detail, elapsedMs, options.threads).also { mutableState.value = it }
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
        configurationId: String = "baseline"
    ): NativeBridge.GenerationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(mutableState.value is State.Ready) { "Load a GGUF model before generation" }
            NativeBridge.generate(
                prompt = prompt,
                temperature = temperature,
                topP = topP,
                frequencyPenalty = frequencyPenalty,
                presencePenalty = presencePenalty,
                maxTokens = maxTokens,
                timeoutMs = timeoutMs,
                seed = seed,
                configurationId = configurationId
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
        configurationId: String = "baseline"
    ): NativeBridge.GenerationResult {
        val prompt = Llama3PromptBuilder.build(systemPrompt, messages).text
        return generate(
            prompt, temperature, topP, frequencyPenalty, presencePenalty,
            maxTokens, timeoutMs, seed, configurationId
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
            mutableState.value = State.NotLoaded
            unloaded
        }
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
