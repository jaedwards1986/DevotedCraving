package com.forgeai.chat.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GGUFLoadValidationTest {
    private val manager = GGUFManager()

    @Test
    fun acceptsSupportedLoadConfiguration() {
        manager.validateOptions(
            GGUFManager.LoadOptions(
                contextSize = 4096,
                threads = 4,
                smokeTimeoutMs = 5_000
            )
        )
    }

    @Test
    fun rejectsContextBelowMinimum() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.validateOptions(GGUFManager.LoadOptions(contextSize = 511))
        }
    }

    @Test
    fun rejectsContextAboveMaximum() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.validateOptions(GGUFManager.LoadOptions(contextSize = 32_769))
        }
    }

    @Test
    fun rejectsThreadCountOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.validateOptions(GGUFManager.LoadOptions(threads = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.validateOptions(GGUFManager.LoadOptions(threads = 33))
        }
    }

    @Test
    fun rejectsSmokeTimeoutOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.validateOptions(GGUFManager.LoadOptions(smokeTimeoutMs = 499))
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.validateOptions(GGUFManager.LoadOptions(smokeTimeoutMs = 30_001))
        }
    }

    @Test
    fun sanitizesImportedModelFileNames() {
        val sanitized = LocalModelManager.sanitizeModelFileName("../../model name.gguf")
        assertEquals(false, sanitized.contains('/'))
        assertEquals(false, sanitized.contains('\\'))
        assertEquals(true, sanitized.endsWith(".gguf"))
    }

    @Test
    fun appendsGgufExtensionWhenMissing() {
        assertEquals("my_model.gguf", LocalModelManager.sanitizeModelFileName("my_model"))
    }
}
