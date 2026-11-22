package com.natasha.local.models

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class HubModel(val id: String, val downloads: Long)
data class HubGgufFile(val name: String, val sizeBytes: Long, val sha256: String?)

object HuggingFaceModelHub {
    private const val API = "https://huggingface.co/api"
    private const val MAX_MODEL_BYTES = 20L * 1024L * 1024L * 1024L
    private val cancelled = AtomicBoolean(false)
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    fun cancelDownload() {
        cancelled.set(true)
        activeConnection.getAndSet(null)?.disconnect()
    }

    fun search(query: String): List<HubModel> {
        require(query.trim().length >= 2) { "Enter at least two search characters" }
        val encoded = Uri.encode(query.trim())
        val array = JSONArray(get("$API/models?search=$encoded&filter=gguf&sort=downloads&direction=-1&limit=20"))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id")
                if (id.contains("/") && !id.contains("..")) add(HubModel(id, item.optLong("downloads", 0L)))
            }
        }
    }

    fun ggufFiles(modelId: String): List<HubGgufFile> {
        require(modelId.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Invalid model repository" }
        val array = JSONArray(get("$API/models/$modelId/tree/main?recursive=false&expand=true"))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("path")
                if (item.optString("type") == "file" && name.endsWith(".gguf", true) && !name.contains("..")) {
                    val lfs = item.optJSONObject("lfs")
                    val oid = lfs?.optString("oid")?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                    val size = item.optLong("size").takeIf { it > 0L } ?: (lfs?.optLong("size") ?: 0L)
                    add(HubGgufFile(name, size, oid))
                }
            }
        }.sortedByDescending { it.sizeBytes }
    }

    fun download(context: Context, modelId: String, file: HubGgufFile, onProgress: (Long, Long) -> Unit): File {
        require(modelId.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Invalid model repository" }
        require(file.name.endsWith(".gguf", true) && !file.name.contains("..")) { "Only GGUF files can be downloaded" }
        require(file.sizeBytes in 1..MAX_MODEL_BYTES) { "Model size is missing or exceeds the 20 GB safety limit" }
        val directory = File(context.filesDir, "models").apply { mkdirs() }.canonicalFile
        val safeName = file.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(directory, safeName).canonicalFile
        require(destination.parentFile == directory) { "Unsafe destination path" }
        require(!destination.exists()) { "A model with this filename already exists" }
        val reserve = 256L * 1024L * 1024L
        require(android.os.StatFs(directory.path).availableBytes >= file.sizeBytes + reserve) {
            "Not enough free storage; keep at least 256 MB available"
        }
        val encodedPath = file.name.split('/').joinToString("/") { Uri.encode(it) }
        val url = "https://huggingface.co/$modelId/resolve/main/$encodedPath?download=true"
        val partial = File(directory, safeName + ".download")
        cancelled.set(false)
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ForgeAI-Android/1.0")
            }
            activeConnection.set(connection)
            try {
                val code = connection.responseCode
                require(code in 200..299) { "Model host returned HTTP $code. Gated models may require authentication." }
                val reported = connection.contentLengthLong
                require(reported <= 0L || reported <= MAX_MODEL_BYTES) { "Download exceeds the 20 GB safety limit" }
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var total = 0L
                        while (true) {
                            check(!cancelled.get()) { "Download cancelled" }
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= file.sizeBytes && total <= MAX_MODEL_BYTES) { "Downloaded data exceeded the expected file size" }
                            output.write(buffer, 0, count)
                            onProgress(total, file.sizeBytes)
                        }
                    }
                }
                require(partial.length() == file.sizeBytes) { "Download size did not match the model catalog" }
                file.sha256?.let { expected ->
                    val actual = sha256(partial)
                    require(actual.equals(expected, true)) { "SHA-256 verification failed; incomplete or altered model removed" }
                }
                require(partial.renameTo(destination)) { "Unable to move verified model into storage" }
                return destination
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
        } catch (failure: Throwable) {
            partial.delete()
            throw failure
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "ForgeAI-Android/1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Model catalog returned HTTP $code" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
