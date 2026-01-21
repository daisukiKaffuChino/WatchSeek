package me.chino.watchseek.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.chino.watchseek.data.*
import me.chino.watchseek.data.network.ChatRequest
import me.chino.watchseek.data.network.ChatStreamResponse
import me.chino.watchseek.data.network.OpenAiChatMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import okio.buffer
import okio.source

class ChatViewModel(val settingsManager: SettingsManager, private val chatHistoryManager: ChatHistoryManager) : ViewModel() {
    private val _history = MutableStateFlow<List<Chat>>(emptyList())
    val history: StateFlow<List<Chat>> = _history.asStateFlow()

    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val apiKey: StateFlow<String?> = settingsManager.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val json = Json { ignoreUnknownKeys = true }
    
    // OkHttpClient without logging body to prevent buffering stream data
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    init {
        viewModelScope.launch {
            chatHistoryManager.history.collect { chats -> _history.value = chats }
        }
        viewModelScope.launch {
            chatHistoryManager.currentChat.collect { chat ->
                if (chat != null) {
                    val currentLocal = _currentChat.value
                    // Only update if current chat matches the stream or local is empty
                    if (currentLocal == null || currentLocal.id == chat.id || currentLocal.id.isEmpty()) {
                        _currentChat.value = chat
                    }
                }
            }
        }
        viewModelScope.launch {
            val initialChat = chatHistoryManager.getOrCreateInitialChat()
            _currentChat.value = initialChat
        }
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
            _error.value = null
        }
    }

    fun clearError() { _error.value = null }

    fun sendMessage(content: String) {
        val current = _currentChat.value ?: return
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

        viewModelScope.launch {
            _isLoading.value = true
            try {
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
                        val source = responseBody.byteStream().source().buffer()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6).trim()
                                if (data == "[DONE]") break
                                
                                try {
                                    val streamResponse = json.decodeFromString<ChatStreamResponse>(data)
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
                                        
                                        // Update state flow to trigger UI refresh
                                        _currentChat.value = streamingChat
                                    }
                                } catch (e: Exception) {
                                    // Skip malformed chunks
                                }
                            }
                        }
                    }
                    // Save and Select in one go to prevent race conditions
                    chatHistoryManager.saveAndSelectChat(_currentChat.value!!)
                }
            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch { chatHistoryManager.deleteChat(chatId) }
    }
}
