package me.chino.watchseek.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.flow.first
import me.chino.watchseek.R
import me.chino.watchseek.data.ChatHistoryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TokenUsageComplicationService : SuspendingComplicationDataSourceService() {

    private val chatHistoryManager by lazy { ChatHistoryManager(applicationContext) }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData("1.2k", "1200 tokens used today")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val dailyUsage = chatHistoryManager.dailyUsage.first()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val tokens = dailyUsage.find { it.date == today }?.totalTokens ?: 0

        // Format tokens for display (e.g., 1200 -> 1.2k if too long, or just the number)
        val displayTokens = if (tokens >= 10000) {
            String.format(Locale.getDefault(), "%.1fk", tokens / 1000.0)
        } else {
            tokens.toString()
        }

        return createComplicationData(displayTokens, "$tokens tokens used today")
    }

    private fun createComplicationData(text: String, contentDescription: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build()
        ).setMonochromaticImage(
            MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.rounded_data_usage_24)
            ).build()
        ).build()
}
