package ai.davoted.craving.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class ArchiveEntry(
    val id: String,
    val title: String,
    val privateFilePath: String,
    val mimeType: String,
    val sourceUrl: String,
    val addedAtMillis: Long,
    val isFavorite: Boolean = false
)

/** Encrypted provenance/favorites index for authentic media imported by the user. */
class LocalArchiveStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "archive")
    private val indexFile = File(directory, "archive-index.enc")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    suspend fun load(): List<ArchiveEntry> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!indexFile.exists()) return@synchronized emptyList()
            try {
                val payload = indexFile.readBytes()
                require(payload.size > 12) { "Archive index is truncated." }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(128, payload.copyOfRange(0, 12))
                )
                json.decodeFromString<List<ArchiveEntry>>(
                    cipher.doFinal(payload.copyOfRange(12, payload.size)).decodeToString()
                )
            } catch (failure: Exception) {
                throw IllegalStateException("Could not decrypt archive index.", failure)
            }
        }
    }

    suspend fun save(entries: List<ArchiveEntry>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(directory.exists() || directory.mkdirs()) { "Could not create private archive." }
            File(directory, ".nomedia").let { if (!it.exists()) it.createNewFile() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val payload = cipher.iv + cipher.doFinal(json.encodeToString(entries).encodeToByteArray())
            val temporary = File(directory, "archive-index.enc.tmp")
            val backup = File(directory, "archive-index.enc.bak")
            try {
                FileOutputStream(temporary).use { stream ->
                    stream.write(payload)
                    stream.fd.sync()
                }
                if (indexFile.exists()) {
                    if (backup.exists()) backup.delete()
                    check(indexFile.renameTo(backup)) { "Could not preserve previous archive index." }
                }
                if (!temporary.renameTo(indexFile)) {
                    if (backup.exists()) backup.renameTo(indexFile)
                    throw IllegalStateException("Could not commit archive index.")
                }
                backup.delete()
            } finally {
                temporary.delete()
            }
        }
    }

    fun mediaDirectory(): File = directory

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "davotedcraving_archive_index_v1"
    }
}
