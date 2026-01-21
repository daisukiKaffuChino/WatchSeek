package me.chino.watchseek.data

import me.chino.watchseek.data.network.ChatRequest
import me.chino.watchseek.data.network.ChatResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: ChatRequest
    ): ChatResponse

    @Streaming
    @POST("v1/chat/completions")
    fun getChatStream(
        @Header("Authorization") apiKey: String,
        @Body request: ChatRequest
    ): Call<ResponseBody>
}
