package ai.davoted.craving.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only JNI validation. Supply a real GGUF model as an instrumentation test asset:
 * app/src/androidTest/assets/gguf/smoke.gguf
 *
 * The model is copied into the target app's private files directory before native loading.
 * This test deliberately calls JniLlamaCppRuntime directly and does not invoke
 * PromptContextAssembler or LlamaCppAdapter.
 */
@RunWith(AndroidJUnit4::class)
class JniLlamaCppSmokeTest {
    @Test
    fun loadsRealGgufAndReturnsTextFromNativeRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val assetName = instrumentation.arguments.getString("ggufAsset")
            ?: "gguf/smoke.gguf"
        val modelFile = File(targetContext.filesDir, "jni-validation/smoke.gguf")
        modelFile.parentFile?.mkdirs()

        instrumentation.context.assets.open(assetName).use { input ->
            modelFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        assertTrue("Copied GGUF model is missing or empty: ${modelFile.absolutePath}", modelFile.isFile && modelFile.length() > 0L)

        val runtime = JniLlamaCppRuntime()
        assertTrue(
            "libdavoted_llama.so did not load in the target process.",
            runtime.isAvailable
        )

        try {
            assertTrue(
                "llama.cpp rejected the supplied GGUF model.",
                runtime.loadModel(modelFile.absolutePath, 2048, 2)
            )

            // Raw text is passed directly to JNI: no prompt assembly or adapter serialization.
            val output = runtime.generate(
                "Reply with a short runtime smoke-test response.",
                48,
                0.0f
            )
            assertTrue("Native generation returned no text.", output.isNotBlank())
        } finally {
            runtime.close()
            modelFile.delete()
            modelFile.parentFile?.delete()
        }
    }
}
