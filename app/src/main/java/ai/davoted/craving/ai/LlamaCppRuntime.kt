package ai.davoted.craving.ai

/**
 * Narrow runtime port. Production uses the JNI implementation; tests can inject a deterministic
 * implementation without loading native code or a multi-gigabyte GGUF file.
 */
interface LlamaCppRuntime : AutoCloseable {
    val isAvailable: Boolean
    fun loadModel(modelPath: String, contextSize: Int, threadCount: Int): Boolean
    fun generate(prompt: String, maxTokens: Int, temperature: Float): String
    fun cancel()
    override fun close()
}

/**
 * JNI contract for the future bundled llama.cpp shared library.
 *
 * The Kotlin adapter deliberately reports unavailable until libdavoted_llama.so is packaged.
 * This prevents a false "local model ready" state when only the UI/adapter code is present.
 */
class JniLlamaCppRuntime : LlamaCppRuntime {
    override val isAvailable: Boolean = nativeLibraryLoaded

    override fun loadModel(modelPath: String, contextSize: Int, threadCount: Int): Boolean {
        check(isAvailable) { "Native llama.cpp library is not packaged." }
        return nativeLoadModel(modelPath, contextSize, threadCount)
    }

    override fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        check(isAvailable) { "Native llama.cpp library is not packaged." }
        return nativeGenerate(prompt, maxTokens, temperature)
    }

    override fun cancel() {
        if (isAvailable) nativeCancel()
    }

    override fun close() {
        if (isAvailable) nativeRelease()
    }

    private external fun nativeLoadModel(modelPath: String, contextSize: Int, threadCount: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeCancel()
    private external fun nativeRelease()

    companion object {
        private val nativeLibraryLoaded: Boolean = try {
            System.loadLibrary("davoted_llama")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
