package com.nexora.app.model

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class GgufHeaderTest {
    @Test
    fun ggufMagicIsExpectedFourByteSequence() {
        assertArrayEquals(
            byteArrayOf(0x47, 0x47, 0x55, 0x46),
            "GGUF".toByteArray(Charsets.US_ASCII)
        )
    }
}
