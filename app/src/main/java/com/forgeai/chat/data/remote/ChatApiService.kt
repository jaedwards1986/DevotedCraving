package com.forgeai.chat.data.remote

import com.forgeai.chat.data.preferences.AppSettings
import com.forgeai.chat.data.remote.dto.ApiMessageDto
import com.forgeai.chat.data.remote.dto.ChatCompletionRequest
import com.forgeai.chat.data.remote.dto.ModelsListResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.IOException
import java.util.concurrent.TimeUnit

interface ChatApiService {
    @Streaming
    @POST
    fun streamChatCompletion(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body request: ChatCompletionRequest
    ): Call<ResponseBody>

    @GET
    suspend fun fetchModels(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ModelsListResponse>
}

interface LlmStreamClient {
    fun streamCompletion(
        settings: AppSettings,
        messages: List<ApiMessageDto>
    ): Flow<String>

    suspend fun fetchAvailableModels(settings: AppSettings): Result<List<String>>

    suspend fun testConnection(settings: AppSettings): Result<String>
}

class OpenAiStreamClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    gson: Gson = GsonBuilder().create()
) : LlmStreamClient {

    private val apiService: ChatApiService = Retrofit.Builder()
        .baseUrl("https://api.openai.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ChatApiService::class.java)

    override fun streamCompletion(
        settings: AppSettings,
        messages: List<ApiMessageDto>
    ): Flow<String> = flow {
        EndpointValidator.validateEndpointUrl(settings.baseUrl)?.let { error ->
            throw IllegalArgumentException(error)
        }
        EndpointValidator.validateModelName(settings.modelName)?.let { error ->
            throw IllegalArgumentException(error)
        }

        val url = EndpointValidator.normalizeChatCompletionsUrl(settings.baseUrl)
        val headers = buildHeaders(settings.apiKey, acceptSse = true)
        val requestBody = ChatCompletionRequest(
            model = settings.modelName.trim(),
            messages = messages,
            temperature = EndpointValidator.clampTemperature(settings.temperature),
            maxTokens = EndpointValidator.clampMaxTokens(settings.maxTokens),
            stream = true
        )

        val call = apiService.streamChatCompletion(url, headers, requestBody)
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorText = response.errorBody()?.string()
                throw IOException(SseStreamParser.extractHttpErrorMessage(response.code(), errorText))
            }

            val body = response.body() ?: throw IOException("Empty response body from API.")
            val contentType = response.headers()["Content-Type"].orEmpty()

            body.use { responseBody ->
                if (contentType.contains("application/json", ignoreCase = true) &&
                    !contentType.contains("event-stream", ignoreCase = true)
                ) {
                    val fullJson = responseBody.string()
                    when (val parsed = SseStreamParser.parseJsonPayload(fullJson)) {
                        is SseParseResult.Token -> emit(parsed.text)
                        is SseParseResult.Error -> throw IOException(parsed.message)
                        SseParseResult.Done, SseParseResult.Ignore -> Unit
                    }
                    return@use
                }

                val source = responseBody.source()
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    when (val parsed = SseStreamParser.parseLine(line)) {
                        is SseParseResult.Token -> emit(parsed.text)
                        is SseParseResult.Done -> break
                        is SseParseResult.Error -> throw IOException(parsed.message)
                        SseParseResult.Ignore -> Unit
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        } finally {
            if (!call.isCanceled) {
                call.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchAvailableModels(settings: AppSettings): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                EndpointValidator.validateEndpointUrl(settings.baseUrl)?.let { error ->
                    throw IllegalArgumentException(error)
                }
                val url = EndpointValidator.normalizeModelsUrl(settings.baseUrl)
                val headers = buildHeaders(settings.apiKey, acceptSse = false)
                val response = apiService.fetchModels(url, headers)
                if (!response.isSuccessful) {
                    val errorText = response.errorBody()?.string()
                    throw IOException(SseStreamParser.extractHttpErrorMessage(response.code(), errorText))
                }
                val models = response.body()?.data
                    ?.map { it.id.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    ?.sorted()
                    .orEmpty()
                if (models.isEmpty()) {
                    throw IOException("Endpoint responded successfully, but returned no model IDs.")
                }
                models
            }
        }

    override suspend fun testConnection(settings: AppSettings): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                EndpointValidator.validateEndpointUrl(settings.baseUrl)?.let { error ->
                    throw IllegalArgumentException(error)
                }
                val url = EndpointValidator.normalizeModelsUrl(settings.baseUrl)
                val headers = buildHeaders(settings.apiKey, acceptSse = false)
                val response = apiService.fetchModels(url, headers)
                if (!response.isSuccessful) {
                    val errorText = response.errorBody()?.string()
                    throw IOException(SseStreamParser.extractHttpErrorMessage(response.code(), errorText))
                }
                val count = response.body()?.data?.size ?: 0
                if (count > 0) {
                    "Connected successfully ($count models available)"
                } else {
                    "Connected successfully to endpoint"
                }
            }
        }

    private fun buildHeaders(apiKey: String, acceptSse: Boolean): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["Content-Type"] = "application/json"
        headers["Accept"] = if (acceptSse) "text/event-stream" else "application/json"
        val cleanKey = apiKey.trim()
        if (cleanKey.isNotEmpty()) {
            headers["Authorization"] = if (cleanKey.startsWith("Bearer ", ignoreCase = true)) {
                cleanKey
            } else {
                "Bearer $cleanKey"
            }
        }
        return headers
    }

    companion object {
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
