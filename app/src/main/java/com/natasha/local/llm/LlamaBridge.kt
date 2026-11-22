package com.natasha.local.llm

object LlamaBridge {
    init { System.loadLibrary("natasha_llama") }
    external fun loadModel(path: String, contextSize: Int, threads: Int): Boolean
    external fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, callback: TokenCallback): Boolean
    external fun cancel()
    external fun unload()
    interface TokenCallback {
        fun onToken(token: String)
        fun onDone()
        fun onError(message: String)
    }
}
