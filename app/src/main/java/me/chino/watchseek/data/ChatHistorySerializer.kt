package me.chino.watchseek.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object ChatHistorySerializer : Serializer<ChatHistory> {
    override val defaultValue: ChatHistory = ChatHistory.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ChatHistory {
        try {
            return ChatHistory.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: ChatHistory, output: OutputStream) = t.writeTo(output)
}
