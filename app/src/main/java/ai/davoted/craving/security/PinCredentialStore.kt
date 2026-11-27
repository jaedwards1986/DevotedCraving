package ai.davoted.craving.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Stores a salted, PBKDF2-derived PIN verifier encrypted by an Android Keystore AES-GCM key.
 * The PIN itself is never persisted.
 */
object PinCredentialStore {
    private const val PREFS = "davotedcraving_pin"
    private const val KEY_ALIAS = "davotedcraving_pin_verifier_v1"
    private const val SALT = "salt"
    private const val IV = "iv"
    private const val VERIFIER = "verifier"
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256

    fun isConfigured(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(VERIFIER)

    fun createPin(context: Context, pin: String): Boolean {
        if (!pin.matches(Regex("[0-9]{6}"))) return false
        return try {
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val verifier = derive(pin, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(verifier)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(SALT, encode(salt))
                .putString(IV, encode(cipher.iv))
                .putString(VERIFIER, encode(encrypted))
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (!pin.matches(Regex("[0-9]{6}"))) return false
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val salt = decode(prefs.getString(SALT, null) ?: return false)
            val iv = decode(prefs.getString(IV, null) ?: return false)
            val encrypted = decode(prefs.getString(VERIFIER, null) ?: return false)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val expected = cipher.doFinal(encrypted)
            val actual = derive(pin, salt)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
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

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
