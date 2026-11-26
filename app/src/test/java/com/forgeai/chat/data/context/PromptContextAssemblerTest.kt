package com.forgeai.chat.data.context

import com.forgeai.chat.nativebridge.Llama3PromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptContextAssemblerTest {

    @Test
    fun fusesCanonicalPersonaAndOnlyConfirmedMemoryIntoExactLlama3Prompt() {
        val persona = "CANONICAL PERSONA: Natasha is a fictional adult character."
        val memories = listOf(
            RetrievedMemory(
                memoryId = "X",
                category = "preference",
                subject = "User",
                content = "prefers curly-haired brunette characters",
                status = RetrievedMemory.Status.CONFIRMED
            ),
            RetrievedMemory(
                memoryId = "Y",
                category = "candidate",
                subject = "User",
                content = "may enjoy a different style",
                status = RetrievedMemory.Status.CANDIDATE
            )
        )

        val assembledSystemPrompt = PromptContextAssembler.assemble(persona, memories)
        val actual = Llama3PromptBuilder.build(
            assembledSystemPrompt,
            listOf(Llama3PromptBuilder.ChatTurn("user", "Continue our conversation."))
        ).text

        val expectedSystemPrompt =
            "CANONICAL PERSONA: Natasha is a fictional adult character.\n\n" +
                "APPLICATION-RETRIEVED CONFIRMED MEMORY\n" +
                "Use these records only as supplied continuity facts. Do not infer additional facts from them.\n" +
                "- [preference] User: prefers curly-haired brunette characters"

        val expected =
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" +
                expectedSystemPrompt +
                "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n" +
                "Continue our conversation.<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n\n"

        assertEquals(expected, actual)
        assertEquals(expectedSystemPrompt, assembledSystemPrompt)
    }

    @Test
    fun leavesPersonaUnchangedWhenNoConfirmedMemoryExists() {
        val persona = "Canonical persona."
        val memories = listOf(
            RetrievedMemory(
                memoryId = "candidate-1",
                category = "preference",
                subject = "User",
                content = "unconfirmed detail",
                status = RetrievedMemory.Status.CANDIDATE
            )
        )

        assertEquals(persona, PromptContextAssembler.assemble(persona, memories))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankPersona() {
        PromptContextAssembler.assemble(" ", emptyList())
    }
}
