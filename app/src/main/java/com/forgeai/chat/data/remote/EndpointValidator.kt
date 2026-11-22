package com.forgeai.chat.data.remote

import java.net.URI

object EndpointValidator {

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    fun validateEndpointUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return "API endpoint URL cannot be empty."
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return "Enter a valid URL (for example, https://api.openai.com/v1)."

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return "Endpoint URL must start with https:// (or http:// for localhost)."
        }

        val host = uri.host?.lowercase()
        if (host.isNullOrBlank()) {
            return "Endpoint URL is missing a valid host name."
        }

        if (scheme == "http" && host !in LOCAL_HOSTS) {
            return "Use HTTPS for remote endpoints. HTTP is only allowed for localhost or 10.0.2.2."
        }

        return null
    }

    fun validateModelName(modelName: String): String? {
        if (modelName.trim().isEmpty()) {
            return "Model identifier cannot be empty."
        }
        return null
    }

    fun normalizeChatCompletionsUrl(rawBaseUrl: String): String {
        val cleaned = rawBaseUrl.trim().trimEnd('/')
        return if (cleaned.endsWith("/chat/completions", ignoreCase = true)) {
            cleaned
        } else {
            "$cleaned/chat/completions"
        }
    }

    fun normalizeModelsUrl(rawBaseUrl: String): String {
        val cleaned = rawBaseUrl.trim().trimEnd('/')
        val base = if (cleaned.endsWith("/chat/completions", ignoreCase = true)) {
            cleaned.removeSuffix("/chat/completions").trimEnd('/')
        } else {
            cleaned
        }
        return if (base.endsWith("/models", ignoreCase = true)) {
            base
        } else {
            "$base/models"
        }
    }

    fun clampTemperature(value: Float): Float = value.coerceIn(0f, 2f)

    fun clampMaxTokens(value: Int): Int = value.coerceIn(64, 32768)
}
