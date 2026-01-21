package me.chino.watchseek.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val stream: Boolean = false
)

@Serializable
data class OpenAiChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val id: String,
    val choices: List<OpenAiChoice>
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiResponseMessage
)

@Serializable
data class OpenAiResponseMessage(
    val role: String,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

// Streaming models
@Serializable
data class ChatStreamResponse(
    val id: String? = null,
    val choices: List<OpenAiStreamChoice>
)

@Serializable
data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class OpenAiStreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)
