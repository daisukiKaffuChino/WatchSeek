package me.chino.watchseek.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.chino.watchseek.data.ChatHistoryManager
import me.chino.watchseek.data.SettingsManager

class ChatViewModelFactory(
    private val context: Context,
    private val settingsManager: SettingsManager, 
    private val chatHistoryManager: ChatHistoryManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context, settingsManager, chatHistoryManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
