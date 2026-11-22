package com.forgeai.chat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointValidatorTest {

    @Test
    fun acceptsHttpsAndLocalHttpEndpoints() {
        assertNull(EndpointValidator.validateEndpointUrl("https://api.openai.com/v1"))
        assertNull(EndpointValidator.validateEndpointUrl("http://localhost:11434/v1"))
        assertNull(EndpointValidator.validateEndpointUrl("http://10.0.2.2:11434/v1"))
    }

    @Test
    fun rejectsRemoteCleartextHttpAndBlankUrls() {
        assertNotNull(EndpointValidator.validateEndpointUrl(""))
        assertNotNull(EndpointValidator.validateEndpointUrl("ftp://example.com/v1"))
        assertNotNull(EndpointValidator.validateEndpointUrl("http://api.example.com/v1"))
    }

    @Test
    fun normalizesChatCompletionsAndModelsUrls() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            EndpointValidator.normalizeChatCompletionsUrl("https://api.openai.com/v1/")
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            EndpointValidator.normalizeChatCompletionsUrl("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            EndpointValidator.normalizeModelsUrl("https://api.openai.com/v1/chat/completions")
        )
    }

    @Test
    fun clampsGenerationBounds() {
        assertEquals(0f, EndpointValidator.clampTemperature(-1f))
        assertEquals(2f, EndpointValidator.clampTemperature(5f))
        assertEquals(64, EndpointValidator.clampMaxTokens(10))
        assertEquals(32768, EndpointValidator.clampMaxTokens(999999))
    }
}
