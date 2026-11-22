package com.forgeai.chat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseStreamParserTest {

    @Test
    fun parsesOpenAiDeltaTokenFrame() {
        val line = """data: {"id":"chatcmpl-1","choices":[{"delta":{"content":"Hello"}}]}"""
        val result = SseStreamParser.parseLine(line)
        assertEquals(SseParseResult.Token("Hello"), result)
    }

    @Test
    fun parsesDoneSentinel() {
        assertEquals(SseParseResult.Done, SseStreamParser.parseLine("data: [DONE]"))
    }

    @Test
    fun ignoresCommentsAndEmptyLines() {
        assertEquals(SseParseResult.Ignore, SseStreamParser.parseLine(""))
        assertEquals(SseParseResult.Ignore, SseStreamParser.parseLine(": keep-alive"))
        assertEquals(SseParseResult.Ignore, SseStreamParser.parseLine("event: ping"))
    }

    @Test
    fun parsesErrorPayloadFromStream() {
        val line = """data: {"error":{"message":"Invalid API key provided"}}"""
        val result = SseStreamParser.parseLine(line)
        assertEquals(SseParseResult.Error("Invalid API key provided"), result)
    }

    @Test
    fun parsesNonStreamingJsonCompletionFallback() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"Full reply"}}]}"""
        val result = SseStreamParser.parseJsonPayload(body)
        assertEquals(SseParseResult.Token("Full reply"), result)
    }

    @Test
    fun extractsReadableHttpErrorMessage() {
        val msg = SseStreamParser.extractHttpErrorMessage(
            401,
            """{"error":{"message":"Incorrect API key"}}"""
        )
        assertEquals("HTTP 401: Incorrect API key", msg)

        val fallback = SseStreamParser.extractHttpErrorMessage(429, null)
        assertTrue(fallback.contains("Rate limit"))
    }
}
