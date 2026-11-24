package com.forgeai.chat.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenArbitrationTest {
    @Test fun fixedPromptProducesStableIdsAndHash() {
        val result = TokenArbitration.run("Natasha core identity", repeats = 5) { text, addBos ->
            assertFalse(addBos)
            text.toByteArray(Charsets.UTF_8).map { it.toInt() }.toIntArray()
        }
        assertTrue(result.deterministic)
        assertEquals(5, result.repeatCount)
        assertEquals(64, result.promptTokenSha256.length)
        assertEquals(result.promptText.toByteArray(Charsets.UTF_8).map { it.toInt() }, result.promptTokenIds)
    }

    @Test fun detectsTokenSequenceDrift() {
        var call = 0
        val result = TokenArbitration.run("system", repeats = 3) { text, _ ->
            call++
            (text.length + if (call == 2) 1 else 0).let { intArrayOf(it) }
        }
        assertFalse(result.deterministic)
    }

    @Test fun vocabularyProbesReturnArtifactSpecificIds() {
        val result = TokenArbitration.run("system") { text, _ ->
            intArrayOf(text.length, text.hashCode())
        }
        assertTrue(result.vocabulary.size >= 8)
        assertEquals("Natasha", result.vocabulary.first().text)
        assertTrue(result.vocabulary.all { it.tokenIds.isNotEmpty() })
    }
}
