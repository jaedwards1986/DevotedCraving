package com.forgeai.chat.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Llama3PromptBuilderTest {
    @Test fun formatsSystemUserAndOpenAssistantTurn() {
        val prompt = Llama3PromptBuilder.build("Natasha system prompt", "Tell me about Natasha").text
        assertTrue(prompt.startsWith("<|begin_of_text|><|start_header_id|>system<|end_header_id|>"))
        assertTrue(prompt.contains("Natasha system prompt<|eot_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>"))
        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
        assertFalse(prompt.endsWith("<|eot_id|>"))
    }

    @Test fun tokenizesWithoutAddingDuplicateBos() {
        var receivedText = ""
        var receivedAddBos = true
        val ids = Llama3PromptBuilder.tokenize("system", "hello") { text, addBos ->
            receivedText = text
            receivedAddBos = addBos
            intArrayOf(128000, 1, 2, 3)
        }
        assertEquals(4, ids.size)
        assertFalse(receivedAddBos)
        assertTrue(receivedText.startsWith("<|begin_of_text|>"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankUserMessage() {
        Llama3PromptBuilder.build("system", " ")
    }

    @Test fun reportsUtf8ByteCount() {
        val built = Llama3PromptBuilder.build("system", "café")
        assertEquals(built.text.toByteArray(Charsets.UTF_8).size, built.estimatedUtf8Bytes)
    }
}
