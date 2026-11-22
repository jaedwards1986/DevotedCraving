package com.natasha.local.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalLlmEngine {
    @Volatile private var loadedPath: String? = null
    private val nativeMutex = Mutex()

    suspend fun load(path: String, contextSize: Int = 2048, threads: Int = 4): Boolean {
        LlamaBridge.cancel()
        return withContext(Dispatchers.IO) {
            nativeMutex.withLock {
                if (loadedPath == path) return@withLock true
                LlamaBridge.unload()
                loadedPath = null
                val ok = LlamaBridge.loadModel(path, contextSize, threads)
                if (ok) loadedPath = path
                ok
            }
        }
    }

    fun stream(prompt: String, maxTokens: Int = 512, temperature: Float = .7f, topP: Float = .9f): Flow<String> =
        callbackFlow {
            val worker = launch(Dispatchers.Default) {
                try {
                    nativeMutex.withLock {
                        val ok = LlamaBridge.generate(
                            prompt,
                            maxTokens,
                            temperature,
                            topP,
                            object : LlamaBridge.TokenCallback {
                                override fun onToken(token: String) {
                                    trySend(token)
                                }

                                override fun onDone() {
                                    close()
                                }

                                override fun onError(message: String) {
                                    close(IllegalStateException(message))
                                }
                            }
                        )
                        if (!ok) close(IllegalStateException("Model is not loaded or inference could not start"))
                    }
                } catch (failure: Throwable) {
                    close(failure)
                }
            }
            awaitClose {
                LlamaBridge.cancel()
                worker.cancel()
            }
        }

    suspend fun unload() {
        LlamaBridge.cancel()
        withContext(Dispatchers.IO) {
            nativeMutex.withLock {
                loadedPath = null
                LlamaBridge.unload()
            }
        }
    }

    fun cancel() = LlamaBridge.cancel()
}
