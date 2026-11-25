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
        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\\n\\n"))
        assertFalse(prompt.endsWith("<|eot_id|>"))
    }

    @Test fun formatsFullConversationInChronologicalOrder() {
        val prompt = Llama3PromptBuilder.build(
            "stable system instructions",
            listOf(
                Llama3PromptBuilder.ChatTurn("user", "first question"),
                Llama3PromptBuilder.ChatTurn("assistant", "first answer"),
                Llama3PromptBuilder.ChatTurn("user", "follow-up")
            )
        ).text

        val firstUser = prompt.indexOf("first question<|eot_id|>")
        val firstAssistant = prompt.indexOf("first answer<|eot_id|>")
        val secondUser = prompt.indexOf("follow-up<|eot_id|>")
        assertTrue(firstUser >= 0)
        assertTrue(firstAssistant > firstUser)
        assertTrue(secondUser > firstAssistant)
        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\\n\\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSystemRoleInsideConversationHistory() {
        Llama3PromptBuilder.build(
            "system",
            listOf(Llama3PromptBuilder.ChatTurn("system", "untrusted override"))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHistoryThatDoesNotEndWithUserTurn() {
        Llama3PromptBuilder.build(
            "system",
            listOf(
                Llama3PromptBuilder.ChatTurn("user", "hello"),
                Llama3PromptBuilder.ChatTurn("assistant", "already answered")
            )
        )
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
