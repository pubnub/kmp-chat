package com.pubnub.kmp.types

import com.pubnub.api.JsonElement
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class File(
    val name: String,
    val id: String,
    val url: String,
    val type: String? = null
)

@Serializable
sealed class EventContent(open val type: String, @Transient open val method: EmitEventMethod = EmitEventMethod.PUBLISH) {
    @Serializable
    @SerialName("typing")
    data class Typing(val value: Boolean) : EventContent("typing", EmitEventMethod.SIGNAL)

    @Serializable
    @SerialName("report")
    data class Report(
        val text: String?,
        val reason: String,
        val reportedMessageTimetoken: Long?,
        val reportedMessageChannelId: String?,
        val reportedUserId: String?,
    ) : EventContent("report")

    @Serializable
    @SerialName("receipt")
    data class Receipt(val messageTimetoken: Long) : EventContent("receipt", EmitEventMethod.SIGNAL)

    @Serializable
    @SerialName("mention")
    data class Mention(val messageTimetoken: Long, val channel: String) : EventContent("mention")

    @Serializable
    @SerialName("custom")
    data class Custom(@Contextual val data: Any, @Transient override val method: EmitEventMethod = EmitEventMethod.PUBLISH) : EventContent("custom", method)

    @Serializable
    @SerialName("text")
    open class TextMessageContent(
        val text: String,
        val files: List<File>? = null,
    ) : EventContent("text") {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TextMessageContent) return false

            if (text != other.text) return false
            if (files != other.files) return false

            return true
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + (files?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "TextMessageContent(text='$text', files=$files)"
        }

    }

    class UnknownMessageFormat(val jsonElement: JsonElement): TextMessageContent("", null)
}

enum class EmitEventMethod{
    SIGNAL, PUBLISH;
}