package com.forgeai.chat.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed interface SseParseResult {
    data class Token(val text: String) : SseParseResult
    data object Done : SseParseResult
    data class Error(val message: String) : SseParseResult
    data object Ignore : SseParseResult
}

object SseStreamParser {

    fun parseLine(rawLine: String): SseParseResult {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith(":")) {
            return SseParseResult.Ignore
        }

        val payload = when {
            line.startsWith("data:") -> line.removePrefix("data:").trim()
            line.startsWith("{") -> line
            else -> return SseParseResult.Ignore
        }

        if (payload.isEmpty()) {
            return SseParseResult.Ignore
        }

        if (payload == "[DONE]") {
            return SseParseResult.Done
        }

        return parseJsonPayload(payload)
    }

    fun parseJsonPayload(jsonPayload: String): SseParseResult {
        val root = runCatching { JsonParser.parseString(jsonPayload) }.getOrNull()
            ?: return SseParseResult.Ignore

        if (!root.isJsonObject) return SseParseResult.Ignore
        val obj = root.asJsonObject

        extractErrorMessage(obj)?.let { errorMsg ->
            return SseParseResult.Error(errorMsg)
        }

        // OpenAI / OpenRouter / Groq / Gemini OpenAI-compat format
        val choices = obj.getAsJsonArray("choices")
        if (choices != null && choices.size() > 0) {
            val first = choices.get(0)
            if (first != null && first.isJsonObject) {
                val choiceObj = first.asJsonObject

                val delta = choiceObj.getAsJsonObject("delta")
                if (delta != null) {
                    val content = delta.stringOrNull("content")
                    if (!content.isNullOrEmpty()) {
                        return SseParseResult.Token(content)
                    }
                }

                val message = choiceObj.getAsJsonObject("message")
                if (message != null) {
                    val content = message.stringOrNull("content")
                    if (!content.isNullOrEmpty()) {
                        return SseParseResult.Token(content)
                    }
                }

                val text = choiceObj.stringOrNull("text")
                if (!text.isNullOrEmpty()) {
                    return SseParseResult.Token(text)
                }

                val finishReason = choiceObj.stringOrNull("finish_reason")
                if (!finishReason.isNullOrBlank()) {
                    return SseParseResult.Done
                }
            }
        }

        // Ollama native streaming fallback
        val ollamaMessage = obj.getAsJsonObject("message")
        if (ollamaMessage != null) {
            val content = ollamaMessage.stringOrNull("content")
            if (!content.isNullOrEmpty()) {
                return SseParseResult.Token(content)
            }
        }

        if (obj.has("done") && obj.get("done").isJsonPrimitive && obj.get("done").asBoolean) {
            return SseParseResult.Done
        }

        return SseParseResult.Ignore
    }

    fun extractHttpErrorMessage(statusCode: Int, errorBody: String?): String {
        val body = errorBody?.trim().orEmpty()
        if (body.isNotEmpty()) {
            val parsed = runCatching { JsonParser.parseString(body) }.getOrNull()
            if (parsed != null && parsed.isJsonObject) {
                extractErrorMessage(parsed.asJsonObject)?.let { detail ->
                    return "HTTP $statusCode: $detail"
                }
            }
            val snippet = body.replace(Regex("\\s+"), " ").take(240)
            return "HTTP $statusCode: $snippet"
        }
        return when (statusCode) {
            401 -> "HTTP 401: Unauthorized. Check your API key in Settings."
            403 -> "HTTP 403: Access denied for this model or endpoint."
            404 -> "HTTP 404: Endpoint or model not found. Verify the Base URL and Model ID."
            429 -> "HTTP 429: Rate limit exceeded. Wait a moment and try again."
            in 500..599 -> "HTTP $statusCode: Provider server error. Please retry shortly."
            else -> "HTTP $statusCode: Request failed."
        }
    }

    private fun extractErrorMessage(obj: JsonObject): String? {
        val errorElem: JsonElement = obj.get("error") ?: return null
        return when {
            errorElem.isJsonPrimitive -> errorElem.asString.takeIf { it.isNotBlank() }
            errorElem.isJsonObject -> {
                val errObj = errorElem.asJsonObject
                errObj.stringOrNull("message")
                    ?: errObj.stringOrNull("detail")
                    ?: errObj.stringOrNull("type")
            }
            else -> null
        }
    }

    private fun JsonObject.stringOrNull(memberName: String): String? {
        val elem = get(memberName) ?: return null
        if (!elem.isJsonPrimitive) return null
        return runCatching { elem.asString }.getOrNull()
    }
}
