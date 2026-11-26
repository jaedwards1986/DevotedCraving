package com.forgeai.chat.nativebridge

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Model-neutral streaming seam between the local inference engine and Conversation Service.
 *
 * GGUFManager remains the owner of model lifecycle/serialization. This adapter owns only
 * per-generation session state and exposes incremental text as a Kotlin Flow.
 */
class LocalGenerationFlowAdapter(
    private val ggufManager: GGUFManager,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    enum class GenerationPhase { QUEUED, ACTIVE, UNWINDING, IDLE }

    private class GenerationSession {
        val monitor = Any()
        var phase: GenerationPhase = GenerationPhase.QUEUED
        var cancellationRequested: Boolean = false
    }

    fun generate(
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        frequencyPenalty: Float = 0f,
        presencePenalty: Float = 0f,
        maxTokens: Int = 128,
        timeoutMs: Int = 120_000,
        seed: Int = -1,
        configurationId: String = "baseline"
    ): Flow<String> = callbackFlow {
        val session = GenerationSession()
        val accepted = synchronized(session.monitor) {
            session.phase = GenerationPhase.QUEUED
            true
        }
        check(accepted)

        val worker: Job = launch(workerDispatcher) {
            try {
                val callback = object : GenerationCallback {
                    override fun onGenerationStarted() {
                        val cancelNow = synchronized(session.monitor) {
                            if (session.phase == GenerationPhase.QUEUED) session.phase = GenerationPhase.ACTIVE
                            session.cancellationRequested
                        }
                        if (cancelNow) {
                            NativeBridge.cancelGeneration()
                        }
                    }

                    override fun onTokenPiece(tokenPiece: String) {
                        if (tokenPiece.isNotEmpty()) trySend(tokenPiece)
                    }
                }

                val result = ggufManager.generate(
                    prompt = prompt,
                    temperature = temperature,
                    topP = topP,
                    frequencyPenalty = frequencyPenalty,
                    presencePenalty = presencePenalty,
                    maxTokens = maxTokens,
                    timeoutMs = timeoutMs,
                    seed = seed,
                    configurationId = configurationId,
                    generationCallback = callback
                )
                if (!result.success) {
                    throw IllegalStateException(result.message.ifBlank { result.status })
                }
            } finally {
                synchronized(session.monitor) {
                    if (session.phase != GenerationPhase.IDLE) {
                        session.phase = GenerationPhase.UNWINDING
                        session.phase = GenerationPhase.IDLE
                    }
                }
            }
        }

        awaitClose {
            val cancelNative = synchronized(session.monitor) {
                when (session.phase) {
                    GenerationPhase.QUEUED, GenerationPhase.ACTIVE -> {
                        session.cancellationRequested = true
                        session.phase == GenerationPhase.ACTIVE
                    }
                    GenerationPhase.UNWINDING, GenerationPhase.IDLE -> false
                }
            }
            if (cancelNative) NativeBridge.cancelGeneration()
            worker.cancel()
            if (!cancelNative) {
                synchronized(session.monitor) {
                    if (session.phase == GenerationPhase.QUEUED) session.phase = GenerationPhase.IDLE
                }
            }
        }
    }


    fun generateChat(
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
    ): Flow<String> {
        val prompt = Llama3PromptBuilder.build(systemPrompt, messages).text
        return generate(
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

    suspend fun collectToString(
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        frequencyPenalty: Float = 0f,
        presencePenalty: Float = 0f,
        maxTokens: Int = 128,
        timeoutMs: Int = 120_000,
        seed: Int = -1,
        configurationId: String = "baseline"
    ): String = withContext(workerDispatcher) {
        val result = StringBuilder()
        generate(
            prompt, temperature, topP, frequencyPenalty, presencePenalty,
            maxTokens, timeoutMs, seed, configurationId
        ).collect { result.append(it) }
        result.toString()
    }
}
