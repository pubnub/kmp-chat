package com.pubnub.chat.types

import com.pubnub.api.JsonElement
import com.pubnub.chat.restrictions.RestrictionType
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class File(
    val name: String,
    val id: String,
    val url: String,
    val type: String? = null
)

@Serializable
sealed class EventContent {
    @Serializable
    @SerialName("typing")
    class Typing(val value: Boolean) : EventContent()

    @Serializable
    @SerialName("report")
    class Report(
        val text: String?,
        val reason: String,
        val reportedMessageTimetoken: Long?,
        val reportedMessageChannelId: String?,
        val reportedUserId: String?,
    ) : EventContent()

    @Serializable
    @SerialName("receipt")
    class Receipt(val messageTimetoken: Long) : EventContent()

    @Serializable
    @SerialName("mention")
    class Mention(val messageTimetoken: Long, val channelId: String, val parentChannelId: String? = null) : EventContent()

    @Serializable
    @SerialName("invite")
    class Invite(val channelType: ChannelType, val channel: String) :
        EventContent() // channel should be channelId, but we can't change it not ot break compatibility with existing Chat SDK

    @Serializable
    @SerialName("custom")
    class Custom(
        @Contextual val data: Any,
        @Transient val method: EmitEventMethod = EmitEventMethod.PUBLISH
    ) : EventContent()

    @Serializable
    @SerialName("moderation")
    class Moderation(val channelId: String, val restriction: RestrictionType, val reason: String? = null) :
        EventContent()

    @Serializable
    @SerialName("text")
    open class TextMessageContent(
        val text: String,
        val files: List<File>? = null,
    ) : EventContent() {
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

    class UnknownMessageFormat(val jsonElement: JsonElement) : TextMessageContent("", null)
}

enum class EmitEventMethod {
    SIGNAL,
    PUBLISH
}
