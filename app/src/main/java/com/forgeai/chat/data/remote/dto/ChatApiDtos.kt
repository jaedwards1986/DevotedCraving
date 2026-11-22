package com.forgeai.chat.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("messages")
    val messages: List<ApiMessageDto>,
    @SerializedName("temperature")
    val temperature: Float,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    @SerializedName("stream")
    val stream: Boolean = true
)

data class ApiMessageDto(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String
)

data class ModelsListResponse(
    @SerializedName("data")
    val data: List<RemoteModelDto>? = null
)

data class RemoteModelDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("owned_by")
    val ownedBy: String? = null
)
