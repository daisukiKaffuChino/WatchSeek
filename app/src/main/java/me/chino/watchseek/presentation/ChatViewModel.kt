package me.chino.watchseek.presentation

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.chino.watchseek.data.*
import me.chino.watchseek.data.network.ChatRequest
import me.chino.watchseek.data.network.ChatStreamResponse
import me.chino.watchseek.data.network.OpenAiChatMessage
import me.chino.watchseek.complication.TokenUsageComplicationService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import okio.buffer
import okio.source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    @SuppressLint("StaticFieldLeak") private val context: Context,
    val settingsManager: SettingsManager,
    private val chatHistoryManager: ChatHistoryManager
) : ViewModel() {
    private val _history = MutableStateFlow<List<Chat>>(emptyList())
    val history: StateFlow<List<Chat>> = _history.asStateFlow()

    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentJob: Job? = null
    private var currentCall: Call<ResponseBody>? = null // 修改为 retrofit2.Call 类型

    val dailyUsage: StateFlow<List<TokenUsage>> = chatHistoryManager.dailyUsage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiKey: StateFlow<String?> = settingsManager.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val json = Json { ignoreUnknownKeys = true }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    init {
        viewModelScope.launch {
            chatHistoryManager.history.collect { chats -> _history.value = chats }
        }
        
        viewModelScope.launch {
            val initialChat = chatHistoryManager.getOrCreateInitialChat()
            _currentChat.value = initialChat
        }
    }

    private fun requestComplicationUpdate() {
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, TokenUsageComplicationService::class.java)
        ).requestUpdateAll()
    }

    private suspend fun getApi(): OpenAiApi {
        val baseUrl = settingsManager.baseUrl.first()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiApi::class.java)
    }

    fun createNewChat() {
        viewModelScope.launch {
            val newChat = chatHistoryManager.generateTemporaryNewChat()
            _currentChat.value = newChat
            _error.value = null
        }
    }

    fun selectChat(chat: Chat) {
        viewModelScope.launch {
            chatHistoryManager.selectChat(chat)
            _currentChat.value = chat 
            _error.value = null
        }
    }

    fun clearError() { _error.value = null }

    fun stopStreaming() {
        currentCall?.cancel()
        currentJob?.cancel()
        _isLoading.value = false
        // 停止后保存已收到的部分回复
        _currentChat.value?.let { partialChat ->
            viewModelScope.launch {
                chatHistoryManager.saveAndSelectChat(partialChat)
            }
        }
    }

    fun sendMessage(content: String) {
        val current = _currentChat.value ?: return
        if (_isLoading.value) return 

        _error.value = null
        
        val userMessage = ChatMessage.newBuilder()
            .setRole("user")
            .setContent(content)
            .setTimestamp(System.currentTimeMillis())
            .build()

        val updatedMessages = current.messagesList.toMutableList().apply { add(userMessage) }
        val isFirstMessage = current.title == "New Chat" && current.messagesCount == 0
        
        val chatWithUser = current.toBuilder()
            .clearMessages()
            .addAllMessages(updatedMessages)
            .apply { if (isFirstMessage) setTitle(content.take(20)) }
            .setTimestamp(System.currentTimeMillis())
            .build()
        
        _currentChat.value = chatWithUser

        currentJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                chatHistoryManager.saveAndSelectChat(chatWithUser)

                val key = settingsManager.apiKey.firstOrNull()
                val model = settingsManager.model.first()
                
                if (key.isNullOrBlank()) {
                    _error.value = "API Key is missing."
                    _isLoading.value = false
                    return@launch
                }

                val api = getApi()
                val chatRequestMessages = chatWithUser.messagesList.map { 
                    OpenAiChatMessage(role = it.role, content = it.content)
                }
                
                val request = ChatRequest(model = model, messages = chatRequestMessages, stream = true)
                val call = api.getChatStream(apiKey = "Bearer $key", request = request)
                currentCall = call // 现在类型匹配了
                
                val response = withContext(Dispatchers.IO) { call.execute() }

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: response.message()
                    _error.value = "Error ${response.code()}: $errorBody"
                    _isLoading.value = false
                    return@launch
                }

                val responseBody = response.body()
                if (responseBody != null) {
                    var assistantContent = ""
                    var assistantReasoning = ""
                    
                    val initialAssistantMessage = ChatMessage.newBuilder()
                        .setRole("assistant")
                        .setContent("")
                        .setTimestamp(System.currentTimeMillis())
                        .build()
                    
                    var streamingChat = chatWithUser.toBuilder().addMessages(initialAssistantMessage).build()
                    _currentChat.value = streamingChat

                    withContext(Dispatchers.IO) {
                        responseBody.byteStream().source().buffer().use { source ->
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: break
                                if (line.isBlank() || !line.startsWith("data: ")) continue
                                
                                val data = line.substring(6).trim()
                                if (data == "[DONE]") break
                                
                                try {
                                    val streamResponse = json.decodeFromString<ChatStreamResponse>(data)
                                    
                                    streamResponse.usage?.let { usage ->
                                        if (usage.totalTokens > 0) {
                                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                            chatHistoryManager.recordTokenUsage(today, usage.totalTokens)
                                            requestComplicationUpdate()
                                        }
                                    }

                                    val delta = streamResponse.choices.firstOrNull()?.delta ?: continue
                                    val contentPart = delta.content ?: ""
                                    val reasoningPart = delta.reasoningContent ?: ""
                                    
                                    if (contentPart.isNotEmpty() || reasoningPart.isNotEmpty()) {
                                        assistantContent += contentPart
                                        assistantReasoning += reasoningPart
                                        
                                        val updatedAssistantMessage = ChatMessage.newBuilder()
                                            .setRole("assistant")
                                            .setContent(assistantContent)
                                            .setReasoningContent(assistantReasoning)
                                            .setTimestamp(System.currentTimeMillis())
                                            .build()
                                        
                                        val lastIdx = streamingChat.messagesCount - 1
                                        val finalMessages = streamingChat.messagesList.toMutableList()
                                        finalMessages[lastIdx] = updatedAssistantMessage
                                        
                                        streamingChat = streamingChat.toBuilder()
                                            .clearMessages()
                                            .addAllMessages(finalMessages)
                                            .build()
                                        
                                        _currentChat.value = streamingChat
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    chatHistoryManager.saveAndSelectChat(_currentChat.value!!)
                }
            } catch (e: Exception) {
                // 如果是手动取消，则不显示错误
                if (e !is java.io.IOException || currentCall?.isCanceled != true) {
                    _error.value = "Connection failed: ${e.localizedMessage}"
                }
                _currentChat.value?.let { partialChat ->
                    chatHistoryManager.saveAndSelectChat(partialChat)
                }
            } finally {
                _isLoading.value = false
                currentJob = null
                currentCall = null
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch { chatHistoryManager.deleteChat(chatId) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            chatHistoryManager.clearAllHistory()
            _currentChat.value = chatHistoryManager.getOrCreateInitialChat()
            requestComplicationUpdate()
        }
    }
}
