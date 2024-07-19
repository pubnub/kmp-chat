package com.pubnub.chat.types

import com.pubnub.api.JsonElement
import com.pubnub.api.PubNubException
import com.pubnub.chat.restrictions.RestrictionType
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

@Serializable
data class File(
    val name: String,
    val id: String,
    val url: String,
    val type: String? = null
)

fun getMethodFor(type: KClass<out EventContent>): EmitEventMethod? {
    return when (type) {
        EventContent.Custom::class -> null
        EventContent.Receipt::class, EventContent.Typing::class -> EmitEventMethod.SIGNAL
        else -> EmitEventMethod.PUBLISH
    }
}

@Serializable
sealed class EventContent {
    @Serializable
    @SerialName("typing")
    data class Typing(val value: Boolean) : EventContent()

    @Serializable
    @SerialName("report")
    data class Report(
        val text: String?,
        val reason: String,
        val reportedMessageTimetoken: Long?,
        val reportedMessageChannelId: String?,
        val reportedUserId: String?,
    ) : EventContent() {
        companion object {
            const val TEXT = "text"
            const val REASON = "reason"
            const val REPORTED_MESSAGE_TIMETOKEN = "reportedMessageTimetoken"
            const val REPORTED_MESSAGE_CHANNEL_ID = "reportedMessageChannelId"
            const val REPORTED_USER_ID = "reportedUserId"
        }
    }

    @Serializable
    @SerialName("receipt")
    data class Receipt(val messageTimetoken: Long) : EventContent()

    @Serializable
    @SerialName("mention")
    data class Mention(val messageTimetoken: Long, val channel: String) : EventContent() {
        companion object {
            const val MESSAGE_TIMETOKEN = "messageTimetoken"
            const val CHANNEL = "channel"
        }
    }

    @Serializable
    @SerialName("invite")
    data class Invite(val channelType: ChannelType, val channelId: String) : EventContent() {
        companion object {
            const val CHANNEL_TYPE = "channelType"
            const val CHANNEL_ID = "channelId"
        }
    }

    @Serializable
    @SerialName("custom")
    data class Custom(
        @Contextual val data: Any,
        @Transient val method: EmitEventMethod = EmitEventMethod.PUBLISH
    ) : EventContent() {
        companion object {
            const val DATA = "data"
            const val METHOD = "method"
        }
    }

    @Serializable
    @SerialName("moderation")
    data class Moderation(val channelId: String, val restriction: RestrictionType, val reason: String? = null) :
        EventContent() {
        companion object {
            const val CHANNEL_ID = "channelId"
            const val RESTRICTION = "restriction"
            const val REASON = "reason"
        }
    }

    @Serializable
    @SerialName("text")
    open class TextMessageContent(
        val text: String,
        val files: List<File>? = null,
    ) : EventContent() {
        companion object {
            const val TEXT = "text"
            const val FILES = "files"
        }

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

enum class EmitEventMethod(val stringValue: String) {
    SIGNAL("signal"),
    PUBLISH("publish");

    companion object {
        fun from(emitEventMethod: String): EmitEventMethod {
            return entries.find { it -> it.stringValue == emitEventMethod }
                ?: throw PubNubException("unknown EmitEventMethod: $emitEventMethod")
        }
    }
}
