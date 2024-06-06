package com.pubnub.kmp.types

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class File(
    val name: String,
    val id: String,
    val url: String,
    val type: String? = null
)

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
        val reportedMessageTimetoken: String?,
        val reportedMessageChannelId: String?,
        val reportedUserId: String?,
    ) : EventContent()

    @Serializable
    @SerialName("receipt")
    data class Receipt(val messageTimetoken: String) : EventContent()

    @Serializable
    @SerialName("mention")
    data class Mention(val messageTimetoken: String, val channel: String) : EventContent()

    @Serializable
    @SerialName("custom")
    data class Custom(@Contextual val data: Any) : EventContent()

    @Serializable
    @SerialName("text")
    data class TextMessageContent(
        val text: String,
        val files: List<File>? = null,
    ) : EventContent()
}

enum class EmitEventMethod{
    SIGNAL, PUBLISH;
}