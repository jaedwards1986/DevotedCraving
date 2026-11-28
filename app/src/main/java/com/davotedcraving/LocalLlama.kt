package com.davotedcraving

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import java.io.File

class LocalModelManager(private val context: Context) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }

    fun models(): List<File> = modelDir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
        ?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun destinationFor(name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(modelDir, safe)
    }

    suspend fun importModel(source: File, name: String = source.name): File = withContext(Dispatchers.IO) {
        require(source.extension.equals("gguf", true)) { "Only GGUF model files are supported." }
        val destination = destinationFor(name)
        source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        destination
    }

    fun modelPath(name: String): String = File(modelDir, name).absolutePath
}

class LocalLlamaEngine {
    private var model: Any? = null

    suspend fun generate(modelPath: String, systemPrompt: String, prompt: String): String {
        return withContext(Dispatchers.Default) {
            @Suppress("UNCHECKED_CAST")
            val loaded = model ?: Llama.loadModel(
                modelPath = modelPath,
                config = LlamaConfig(contextSize = 4096, threads = 6)
            ).also { model = it }
            val result = Llama.complete(
                loaded,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = 384
            )
            result.text.trim()
        }
    }

    suspend fun release() {
        val loaded = model ?: return
        withContext(Dispatchers.Default) {
            @Suppress("UNCHECKED_CAST")
            Llama.releaseModel(loaded)
            model = null
        }
    }
}
