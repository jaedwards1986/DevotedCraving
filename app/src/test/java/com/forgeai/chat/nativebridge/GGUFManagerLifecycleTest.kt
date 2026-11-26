package com.forgeai.chat.nativebridge

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GGUFManagerLifecycleTest {
    private val metadata = GgufMetadataReader.ModelMetadata(
        architecture = "test",
        embeddingDimension = 32,
        declaredContextLength = 4096,
        fileSize = 4L
    )

    @Test
    fun readyRequiresNativeLoadAndSmokeGeneration() = runBlocking {
        val backend = FakeBackend()
        val file = fakeGguf()
        try {
            val manager = GGUFManager(backend) { metadata }
            val result = manager.load(file)
            assertEquals(GGUFManager.ReadinessStatus.READY_FOR_CHAT, manager.readiness.value?.status)
            assertEquals(file.canonicalPath, result.path)
            assertEquals(1, backend.loadCalls)
            assertEquals(1, backend.generateCalls)
            assertTrue(manager.state.value is GGUFManager.State.Ready)
        } finally {
            file.delete()
        }
    }

    @Test
    fun nativeLoadFailureIsClassifiedAndCleanedUp() = runBlocking {
        val backend = FakeBackend(loadSuccess = false)
        val file = fakeGguf()
        try {
            val manager = GGUFManager(backend) { metadata }
            runCatching { manager.load(file) }
            assertEquals(GGUFManager.ReadinessStatus.LOAD_FAILURE, manager.readiness.value?.status)
            assertEquals(1, backend.unloadCalls)
            assertFalse(manager.state.value is GGUFManager.State.Ready)
        } finally {
            file.delete()
        }
    }

    @Test
    fun smokeFailureIsClassifiedAndCleanedUpExactlyOnce() = runBlocking {
        val backend = FakeBackend(smokeSuccess = false)
        val file = fakeGguf()
        try {
            val manager = GGUFManager(backend) { metadata }
            runCatching { manager.load(file) }
            assertEquals(GGUFManager.ReadinessStatus.SMOKE_TEST_FAILURE, manager.readiness.value?.status)
            assertEquals(1, backend.unloadCalls)
            assertFalse(manager.state.value is GGUFManager.State.Ready)
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatedHeaderIsRejectedBeforeNativeLoad() = runBlocking {
        val file = File.createTempFile("truncated", ".gguf")
        file.writeBytes(byteArrayOf(0x47, 0x47))
        val backend = FakeBackend()
        try {
            val manager = GGUFManager(backend) { metadata }
            runCatching { manager.load(file) }
            assertEquals(GGUFManager.ReadinessStatus.INVALID_FILE, manager.readiness.value?.status)
            assertEquals(0, backend.loadCalls)
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatedGgufMetadataIsRejectedBeforeNativeLoad() = runBlocking {
        val file = File.createTempFile("truncated-metadata", ".gguf")
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        val backend = FakeBackend()
        try {
            val manager = GGUFManager(backend)
            runCatching { manager.load(file) }
            assertEquals(GGUFManager.ReadinessStatus.INVALID_METADATA, manager.readiness.value?.status)
            assertEquals(0, backend.loadCalls)
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingFileNeverReachesNativeBackend() = runBlocking {
        val file = File.createTempFile("missing", ".gguf")
        file.delete()
        val backend = FakeBackend()
        val manager = GGUFManager(backend) { metadata }
        runCatching { manager.load(file) }
        assertEquals(GGUFManager.ReadinessStatus.INVALID_FILE, manager.readiness.value?.status)
        assertEquals(0, backend.loadCalls)
    }

    @Test
    fun contextBeyondModelDeclarationIsRejectedBeforeNativeLoad() = runBlocking {
        val file = fakeGguf()
        val backend = FakeBackend()
        try {
            val manager = GGUFManager(backend) { metadata }
            runCatching { manager.load(file, GGUFManager.LoadOptions(contextSize = 8192)) }
            assertEquals(GGUFManager.ReadinessStatus.INVALID_CONTEXT, manager.readiness.value?.status)
            assertEquals(0, backend.loadCalls)
        } finally {
            file.delete()
        }
    }

    private fun fakeGguf(): File = File.createTempFile("valid-header", ".gguf").apply {
        writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46))
    }

    private class FakeBackend(
        private val loadSuccess: Boolean = true,
        private val smokeSuccess: Boolean = true
    ) : NativeModelBackend {
        var loadCalls = 0
        var generateCalls = 0
        var unloadCalls = 0
        private var loaded = false

        override fun getModelStatus() = NativeModelStatus(loaded)

        override fun loadModel(path: String, contextSize: Int, threads: Int): NativeBridge.ModelLoadResult {
            loadCalls++
            loaded = loadSuccess
            return NativeBridge.ModelLoadResult(loadSuccess, if (loadSuccess) "loaded" else "native load failed")
        }

        override fun tokenize(text: String, addBos: Boolean) = intArrayOf(1, 2)

        override fun generate(
            prompt: String,
            temperature: Float,
            topP: Float,
            frequencyPenalty: Float,
            presencePenalty: Float,
            maxTokens: Int,
            timeoutMs: Int,
            seed: Int,
            configurationId: String,
            generationCallback: GenerationCallback?
        ) : NativeBridge.GenerationResult {
            generateCalls++
            return NativeBridge.GenerationResult(
                status = if (smokeSuccess) "OK" else "ERROR",
                configurationId = configurationId,
                message = if (smokeSuccess) "" else "simulated smoke failure",
                text = if (smokeSuccess) "OK" else "",
                promptTokens = 1,
                outputTokens = if (smokeSuccess) 1 else 0,
                elapsedMs = 1,
                tokensPerSecond = 1.0,
                reachedEndOfGeneration = true,
                tokenIds = listOf(1)
            )
        }

        override fun cancelGeneration() = true

        override fun unloadModel(): Boolean {
            unloadCalls++
            loaded = false
            return true
        }
    }
}
