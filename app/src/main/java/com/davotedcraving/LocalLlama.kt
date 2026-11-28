package com.davotedcraving

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class LocalModelManager(private val context: Context) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("davoted_models", Context.MODE_PRIVATE)
    private val importMutex = Mutex()

    fun models(): List<File> = modelDir.listFiles { f ->
        f.isFile && f.extension.equals("gguf", true) && !f.name.endsWith(".part")
    }?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun selectedModel(): File? {
        val selected = preferences.getString("selected_model", null)
        return selected?.let { File(modelDir, it).takeIf(File::isFile) } ?: models().firstOrNull()
    }

    fun selectModel(file: File) {
        require(file.parentFile?.canonicalFile == modelDir.canonicalFile &&
            file.isFile && file.extension.equals("gguf", true)) { "Select an installed GGUF model." }
        preferences.edit().putString("selected_model", file.name).apply()
    }

    private fun reserveDestination(name: String): File {
        val cleaned = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120).let { if (it.endsWith(".gguf", true)) it else "$it.gguf" }
        require(cleaned.isNotBlank() && cleaned != ".gguf") { "Choose a valid GGUF filename." }
        val stem = cleaned.removeSuffix(".gguf")
        var index = 0
        while (true) {
            val candidate = File(modelDir, if (index == 0) "$stem.gguf" else "$stem-$index.gguf")
            if (candidate.createNewFile()) return candidate
            index++
        }
    }

    private suspend fun copyVerified(open: () -> java.io.InputStream, name: String): File =
        importMutex.withLock {
            val destination = reserveDestination(name)
            val temporary = File(modelDir, destination.name + ".part")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                open().use { raw ->
                    DigestInputStream(raw, digest).use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                }
                coroutineContext.ensureActive()
                require(temporary.length() >= 4) { "The selected file is empty or truncated." }
                FileInputStream(temporary).use { input ->
                    val magic = ByteArray(4)
                    require(input.read(magic) == 4 &&
                        magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                        "The selected file does not have a GGUF header."
                    }
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                check(temporary.renameTo(destination)) { "Unable to finalize imported model." }
                preferences.edit()
                    .putString("selected_model", destination.name)
                    .putString("sha256_${destination.name}", sha256)
                    .apply()
                destination
            } catch (cancelled: CancellationException) {
                temporary.delete()
                destination.delete()
                throw cancelled
            } catch (failure: Throwable) {
                temporary.delete()
                destination.delete()
                throw failure
            }
        }

    suspend fun importModel(source: File, name: String = source.name): File = withContext(Dispatchers.IO) {
        require(name.endsWith(".gguf", true)) { "Only GGUF model files are supported." }
        copyVerified({ source.inputStream() }, name)
    }

    suspend fun importUri(uri: Uri, name: String): File = withContext(Dispatchers.IO) {
        require(name.endsWith(".gguf", true)) { "Only GGUF model files are supported." }
        copyVerified({
            context.contentResolver.openInputStream(uri) ?: error("Unable to open selected model file.")
        }, name)
    }

    fun modelPath(name: String): String = File(modelDir, name).absolutePath
}

class LocalLlamaEngine {
    private var model: LlamaModel? = null
    private var loadedPath: String? = null
    private val engineMutex = Mutex()

    suspend fun generate(modelPath: String, systemPrompt: String, prompt: String): String =
        engineMutex.withLock {
            withContext(Dispatchers.Default) {
                if (loadedPath != modelPath) {
                    model?.let { Llama.releaseModel(it) }
                    model = null
                    loadedPath = null
                }
                val loaded = model ?: Llama.loadModel(
                    modelPath = modelPath,
                    config = LlamaConfig(contextSize = 4096, threads = 6)
                ).also {
                    model = it
                    loadedPath = modelPath
                }
                val result = Llama.complete(
                    loaded,
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    maxTokens = 384
                )
                result.text.trim()
            }
        }

    suspend fun release() = engineMutex.withLock {
        withContext(Dispatchers.Default) {
            model?.let { Llama.releaseModel(it) }
            model = null
            loadedPath = null
        }
    }
}
