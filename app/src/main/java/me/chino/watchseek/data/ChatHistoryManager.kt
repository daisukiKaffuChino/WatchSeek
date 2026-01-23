package me.chino.watchseek.data

import android.content.Context
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.chatHistoryDataStore by dataStore(
    fileName = "chat_history.pb",
    serializer = ChatHistorySerializer
)

class ChatHistoryManager(private val context: Context) {

    private val chatHistoryFlow: Flow<ChatHistory> = context.chatHistoryDataStore.data

    val currentChat: Flow<Chat?> = chatHistoryFlow.map { history ->
        history.chatsList.find { it.id == history.currentChatId }
    }

    val history: Flow<List<Chat>> = chatHistoryFlow.map { it.chatsList.sortedByDescending { chat -> chat.timestamp } }

    val dailyUsage: Flow<List<TokenUsage>> = chatHistoryFlow.map { it.dailyUsageList }

    suspend fun saveChat(chat: Chat) {
        context.chatHistoryDataStore.updateData {
            val existingChats = it.chatsList.toMutableList()
            val index = existingChats.indexOfFirst { c -> c.id == chat.id }
            if (index != -1) {
                existingChats[index] = chat
            } else {
                existingChats.add(chat)
            }
            it.toBuilder().clearChats().addAllChats(existingChats).build()
        }
    }

    suspend fun saveAndSelectChat(chat: Chat) {
        context.chatHistoryDataStore.updateData { history ->
            val existingChats = history.chatsList.toMutableList()
            val index = existingChats.indexOfFirst { c -> c.id == chat.id }
            if (index != -1) {
                existingChats[index] = chat
            } else {
                existingChats.add(chat)
            }
            history.toBuilder()
                .clearChats()
                .addAllChats(existingChats)
                .setCurrentChatId(chat.id)
                .build()
        }
    }

    suspend fun recordTokenUsage(date: String, tokens: Int) {
        context.chatHistoryDataStore.updateData { history ->
            val usageList = history.dailyUsageList.toMutableList()
            val index = usageList.indexOfFirst { it.date == date }
            
            if (index != -1) {
                val current = usageList[index]
                usageList[index] = current.toBuilder()
                    .setTotalTokens(current.totalTokens + tokens)
                    .build()
            } else {
                usageList.add(TokenUsage.newBuilder()
                    .setDate(date)
                    .setTotalTokens(tokens)
                    .build())
            }
            
            history.toBuilder()
                .clearDailyUsage()
                .addAllDailyUsage(usageList)
                .build()
        }
    }

    fun generateTemporaryNewChat(): Chat {
        return Chat.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle("New Chat")
            .setTimestamp(System.currentTimeMillis())
            .build()
    }

    suspend fun createAndSelectNewChat(): Chat {
        val newChat = generateTemporaryNewChat()
        context.chatHistoryDataStore.updateData {
            it.toBuilder().addChats(newChat).setCurrentChatId(newChat.id).build()
        }
        return newChat
    }

    suspend fun selectChat(chat: Chat) {
        context.chatHistoryDataStore.updateData {
            it.toBuilder().setCurrentChatId(chat.id).build()
        }
    }

    suspend fun getOrCreateInitialChat(): Chat {
        val historyFlow = chatHistoryFlow.first()
        return if (historyFlow.hasCurrentChatId() && historyFlow.currentChatId.isNotEmpty()) {
            historyFlow.chatsList.find { it.id == historyFlow.currentChatId } ?: createAndSelectNewChat()
        } else if (historyFlow.chatsList.isNotEmpty()) {
            val firstChat = historyFlow.chatsList.first()
            selectChat(firstChat)
            firstChat
        } else {
            createAndSelectNewChat()
        }
    }

    suspend fun deleteChat(chatId: String) {
        context.chatHistoryDataStore.updateData { currentHistory ->
            val updatedChats = currentHistory.chatsList.filter { it.id != chatId }
            val newCurrentChatId = if (currentHistory.currentChatId == chatId) {
                updatedChats.firstOrNull()?.id ?: ""
            } else {
                currentHistory.currentChatId
            }
            currentHistory.toBuilder()
                .clearChats()
                .addAllChats(updatedChats)
                .setCurrentChatId(newCurrentChatId)
                .build()
        }
    }

    suspend fun clearAllHistory() {
        context.chatHistoryDataStore.updateData {
            it.toBuilder()
                .clearChats()
                .setCurrentChatId("")
                .build()
        }
    }
}
