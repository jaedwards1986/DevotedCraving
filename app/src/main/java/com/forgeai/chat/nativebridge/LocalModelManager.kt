package com.forgeai.chat.nativebridge

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns app-private GGUF storage and delegates validation/loading to GGUFManager.
 *
 * Import is atomic: the source is copied to a temporary file, hashed, then renamed into
 * the models directory. GGUFManager.load() remains the single authority for model readiness.
 */
class LocalModelManager(
    context: Context,
    private val ggufManager: GGUFManager
) {
    data class ImportedModel(
        val file: File,
        val sha256: String,
        val sizeBytes: Long
    )

    private val contentResolver = context.contentResolver
    private val modelsDirectory = File(context.filesDir, "models").apply { mkdirs() }

    suspend fun importAndLoad(
        source: Uri,
        options: GGUFManager.LoadOptions = GGUFManager.LoadOptions()
    ): ImportedModel = withContext(Dispatchers.IO) {
        val imported = importModel(source)
        ggufManager.load(imported.file, options)
        imported
    }

    suspend fun importModel(source: Uri): ImportedModel = withContext(Dispatchers.IO) {
        val baseName = source.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: "model-${System.currentTimeMillis()}.gguf"
        val fileName = sanitizeModelFileName(baseName)
        val destination = File(modelsDirectory, fileName)
        val temp = File(modelsDirectory, ".$fileName.importing")

        contentResolver.openInputStream(source)?.use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        } ?: error("Unable to open selected model file")

        if (temp.length() < 4L) {
            temp.delete()
            error("Imported model file is too small")
        }

        val digest = sha256(temp)
        if (destination.exists()) destination.delete()
        check(temp.renameTo(destination)) {
            "Unable to finalize imported model file"
        }

        ImportedModel(
            file = destination,
            sha256 = digest,
            sizeBytes = destination.length()
        )
    }

    fun listImportedModels(): List<File> =
        modelsDirectory
            .listFiles { file -> file.isFile && file.extension.equals("gguf", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    fun modelsDirectory(): File = modelsDirectory

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun sanitizeModelFileName(name: String): String {
            val cleaned = name
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('_', '.', ' ')
                .ifBlank { "model" }
            return if (cleaned.endsWith(".gguf", ignoreCase = true)) cleaned else "$cleaned.gguf"
        }
    }
}
