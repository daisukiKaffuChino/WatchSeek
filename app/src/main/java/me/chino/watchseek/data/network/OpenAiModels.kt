package me.chino.watchseek.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val stream: Boolean = false,
    @SerialName("stream_options")
    val streamOptions: StreamOptions? = null
)

@Serializable
data class StreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean
)

@Serializable
data class OpenAiChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val id: String,
    val choices: List<ChatChoice>,
    val usage: Usage? = null
)

@Serializable
data class ChatChoice(
    val message: OpenAiChatMessage
)

@Serializable
data class ChatStreamResponse(
    val id: String,
    val choices: List<ChatStreamChoice>,
    val usage: Usage? = null
)

@Serializable
data class ChatStreamChoice(
    val delta: ChatStreamDelta
)

@Serializable
data class ChatStreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)
