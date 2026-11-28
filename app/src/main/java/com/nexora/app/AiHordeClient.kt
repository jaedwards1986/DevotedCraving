package com.nexora.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HordeModel(
    val name: String,
    val workers: Int,
    val queued: Int,
    val etaSeconds: Int
)

class AiHordeClient(
    private val baseUrl: String = "https://aihorde.net/api/v2",
    private val clientAgent: String = "NEXORA:0.1.0:android"
) {
    suspend fun getTextModels(apiKey: String): List<HordeModel> {
        val response = request(
            path = "/status/models?type=text",
            method = "GET",
            apiKey = apiKey
        )
        val array = JSONArray(response)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val workers = item.optInt("count", 0)
                if (workers > 0) {
                    add(
                        HordeModel(
                            name = item.optString("name"),
                            workers = workers,
                            queued = item.optInt("queued", 0),
                            etaSeconds = item.optInt("eta", 0)
                        )
                    )
                }
            }
        }.sortedByDescending { it.workers }
    }

    suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String?
    ): String {
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("params", JSONObject().apply {
                put("max_context_length", 2048)
                put("max_length", 300)
                put("temperature", 0.85)
                put("top_p", 0.92)
                put("rep_pen", 1.08)
            })
            if (!model.isNullOrBlank()) put("models", JSONArray().put(model))
            put("trusted_workers", false)
        }

        val submitted = JSONObject(
            request("/generate/text/async", "POST", apiKey, payload.toString())
        )
        val requestId = submitted.optString("id")
        if (requestId.isBlank()) {
            throw IllegalStateException(submitted.optString("message", "AI Horde did not return a request ID."))
        }

        try {
            repeat(120) {
                delay(1500)
                val status = JSONObject(
                    request("/generate/text/status/$requestId", "GET", apiKey)
                )
                if (status.optBoolean("faulted", false)) {
                    throw IllegalStateException("The AI Horde worker reported a generation fault.")
                }
                if (status.optBoolean("done", false)) {
                    val generations = status.optJSONArray("generations")
                    if (generations != null && generations.length() > 0) {
                        return generations.getJSONObject(0).optString("text").trim()
                    }
                    throw IllegalStateException("The request finished without returning text.")
                }
            }
            throw IllegalStateException("Timed out waiting for an AI Horde worker.")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            withContext(Dispatchers.IO) {
                runCatching {
                    request("/generate/text/status/$requestId", "DELETE", apiKey)
                }
            }
            throw cancelled
        }
    }

    private suspend fun request(
        path: String,
        method: String,
        apiKey: String,
        body: String? = null
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", apiKey.ifBlank { "0000000000" })
            setRequestProperty("Client-Agent", clientAgent)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).optString("message")
                }.getOrDefault(responseText)
                throw IllegalStateException("AI Horde error $code: $message")
            }
            responseText
        } finally {
            connection.disconnect()
        }
    }
}
