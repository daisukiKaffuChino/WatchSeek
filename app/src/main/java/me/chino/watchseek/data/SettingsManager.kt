package me.chino.watchseek.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    private val API_KEY = stringPreferencesKey("api_key")
    private val MODEL = stringPreferencesKey("model")
    private val BASE_URL = stringPreferencesKey("base_url")
    private val AUTO_HIDE_CHAT_BUTTON = booleanPreferencesKey("auto_hide_chat_button")

    val apiKey: Flow<String?> = context.dataStore.data.map { it[API_KEY] }
    val model: Flow<String> = context.dataStore.data.map { it[MODEL] ?: "gpt-3.5-turbo" }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[BASE_URL] ?: "https://api.openai.com/" }
    val autoHideChatButton: Flow<Boolean> = context.dataStore.data.map { it[AUTO_HIDE_CHAT_BUTTON] ?: true }

    suspend fun saveSettings(apiKey: String, model: String, baseUrl: String) {
        context.dataStore.edit {
            it[API_KEY] = apiKey
            it[MODEL] = model
            it[BASE_URL] = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        }
    }

    suspend fun saveAutoHideChatButton(enabled: Boolean) {
        context.dataStore.edit {
            it[AUTO_HIDE_CHAT_BUTTON] = enabled
        }
    }
}
