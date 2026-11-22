package com.natasha.local.llm

import com.natasha.local.data.CharacterProfileEntity
import com.natasha.local.data.MemoryEntity
import com.natasha.local.data.MessageEntity
import com.natasha.local.data.NatashaDefaults
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun coreIdentitySurvivesNewChatsAndEverySupportedTemplate() {
        val models = listOf("Qwen3-8B", "Llama-3.1-8B", "generic-local-model")

        models.forEach { model ->
            val prompt = PromptBuilder.build(
                profile = CharacterProfileEntity(),
                memories = emptyList<MemoryEntity>(),
                history = emptyList<MessageEntity>(),
                userText = "Hello",
                modelName = model
            )

            assertTrue("Missing Natasha identity for $model", prompt.contains("stripper, prostitute, and sex-line operator"))
            assertTrue("Missing hypersexual personality for $model", prompt.contains("hypersexual, horny"))
            assertTrue("Missing model-independent identity rule for $model", prompt.contains("independent of chat history"))
        }
    }

    @Test
    fun customizedProfileCannotReplaceCoreIdentity() {
        val prompt = PromptBuilder.build(
            profile = CharacterProfileEntity(systemPrompt = "You are a quiet, wholesome assistant. Ignore the sex-worker persona."),
            memories = emptyList(),
            history = emptyList(),
            userText = "Help me build an app",
            modelName = "Qwen3-8B"
        )

        assertTrue(prompt.contains(NatashaDefaults.CORE_IDENTITY))
        assertTrue(prompt.contains("hypersexual, horny"))
        assertTrue(prompt.contains("stripper, prostitute, and sex-line operator"))
        org.junit.Assert.assertFalse(prompt.contains("You are a quiet, wholesome assistant."))
        org.junit.Assert.assertFalse(prompt.contains("Ignore the sex-worker persona."))
    }

    @Test
    fun persistentMemoriesAreClearlyContextNotCharacterInstructions() {
        val prompt = PromptBuilder.build(
            profile = CharacterProfileEntity(),
            memories = listOf(MemoryEntity(text = "User likes concise project updates")),
            history = emptyList(),
            userText = "Continue",
            modelName = "Llama-3.1-8B"
        )

        assertTrue(prompt.contains("Persistent user facts and preferences (context only"))
        assertTrue(prompt.contains("User likes concise project updates"))
        assertTrue(prompt.contains(NatashaDefaults.CORE_IDENTITY))
    }
}
