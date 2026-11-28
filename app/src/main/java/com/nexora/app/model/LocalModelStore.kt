package com.nexora.app.model

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * App-private storage for imported GGUF files.
 *
 * Import is streamed to a temporary file, checked for the GGUF magic header,
 * hashed, and atomically moved into the models directory. No model is loaded
 * into memory by this class.
 */
class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val preferences = appContext.getSharedPreferences("local_models", Context.MODE_PRIVATE)

    suspend fun import(uri: Uri, suggestedName: String?): LocalModel = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(suggestedName ?: "imported-model.gguf")
        require(safeName.lowercase().endsWith(".gguf")) { "Choose a .gguf model file." }

        val finalFile = uniqueDestination(safeName)
        val temporaryFile = File(modelsDir, "${finalFile.name}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var firstBytes = ByteArray(0)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (firstBytes.size < 4) {
                            val needed = 4 - firstBytes.size
                            firstBytes += buffer.copyOfRange(0, minOf(count, needed))
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        totalBytes += count
                    }
                    require(firstBytes.size == 4 &&
                        firstBytes.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                        "This file does not have a valid GGUF header."
                    }
                }
            } ?: error("Unable to open the selected file.")
            require(totalBytes >= 4L) { "The selected model file is empty." }
            check(temporaryFile.renameTo(finalFile)) { "Could not move the imported model into storage." }

            LocalModel(
                fileName = finalFile.name,
                sizeBytes = totalBytes,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                importedAtMillis = System.currentTimeMillis()
            )
        } catch (error: Throwable) {
            temporaryFile.delete()
            finalFile.delete()
            throw error
        }
    }

    suspend fun list(): List<LocalModel> = withContext(Dispatchers.IO) {
        modelsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            ?.map { file ->
                LocalModel(
                    fileName = file.name,
                    sizeBytes = file.length(),
                    sha256 = "",
                    importedAtMillis = file.lastModified()
                )
            }
            ?.sortedByDescending { it.importedAtMillis }
            ?: emptyList()
    }

    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        val target = File(modelsDir, sanitizeFileName(fileName)).canonicalFile
        require(target.parentFile == modelsDir.canonicalFile) { "Invalid model path." }
        check(target.delete()) { "Could not delete model file." }
        if (preferences.getString(KEY_SELECTED, null) == fileName) {
            preferences.edit().remove(KEY_SELECTED).apply()
        }
    }

    fun selectedModel(): String? = preferences.getString(KEY_SELECTED, null)

    fun select(fileName: String) {
        val target = File(modelsDir, sanitizeFileName(fileName)).canonicalFile
        require(target.parentFile == modelsDir.canonicalFile && target.isFile) { "Model file not found." }
        preferences.edit().putString(KEY_SELECTED, target.name).apply()
    }

    fun modelFile(fileName: String): File {
        val target = File(modelsDir, sanitizeFileName(fileName)).canonicalFile
        require(target.parentFile == modelsDir.canonicalFile && target.isFile) { "Model file not found." }
        return target
    }

    private fun uniqueDestination(name: String): File {
        val base = name.removeSuffix(".gguf").removeSuffix(".GGUF")
        var candidate = File(modelsDir, "$name")
        var index = 2
        while (candidate.exists()) {
            candidate = File(modelsDir, "$base ($index).gguf")
            index++
        }
        return candidate
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._() -]"), "_")
            .trim()
            .take(120)
        require(cleaned.isNotBlank() && cleaned != "." && cleaned != "..") { "Invalid model filename." }
        return cleaned
    }

    private companion object {
        const val KEY_SELECTED = "selected_model"
    }
}
