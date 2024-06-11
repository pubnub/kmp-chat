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
sealed class EventContent(@Transient open val method: EmitEventMethod = EmitEventMethod.PUBLISH) {
    @Serializable
    @SerialName("typing")
    data class Typing(val value: Boolean) : EventContent(EmitEventMethod.SIGNAL)

    @Serializable
    @SerialName("report")
    data class Report(
        val text: String?,
        val reason: String,
        val reportedMessageTimetoken: Long?,
        val reportedMessageChannelId: String?,
        val reportedUserId: String?,
    ) : EventContent()

    @Serializable
    @SerialName("receipt")
    data class Receipt(val messageTimetoken: Long) : EventContent(EmitEventMethod.SIGNAL)

    @Serializable
    @SerialName("mention")
    data class Mention(val messageTimetoken: Long, val channel: String) : EventContent()

    @Serializable
    @SerialName("custom")
    data class Custom(@Contextual val data: Any, @Transient override val method: EmitEventMethod = EmitEventMethod.PUBLISH) : EventContent(method)

    @Serializable
    @SerialName("text")
    open class TextMessageContent(
        val text: String,
        val files: List<File>? = null,
    ) : EventContent()

    class UnknownMessageFormat(val jsonElement: JsonElement): TextMessageContent("", null)
}

enum class EmitEventMethod{
    SIGNAL, PUBLISH;
}