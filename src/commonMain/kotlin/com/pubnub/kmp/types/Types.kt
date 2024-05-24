package com.pubnub.kmp.types

//data class TextMessageContent(
//    val type: MessageType = MessageType.TEXT,
//    val text: String,
//    val files: List<File>? = null
//) {
//}

enum class MessageType(type: String) {
    TEXT("text")
}

data class File(
    val name: String,
    val id: String,
    val url: String,
    val type: String? = null
)

data class Action(
    val uuid: String,
    val actionTimetoken: Any // Can be String or Number
)

typealias MessageActions = Map<String, Map<String, List<Action>>>

sealed class EventContent {
    data class Typing(val value: Boolean, val type: String = "typing") : EventContent()
    data class Report(
        val text: String?,
        val reason: String,
        val reportedMessageTimetoken: String?,
        val reportedMessageChannelId: String?,
        val reportedUserId: String?,
        val type: String = "report"
    ) : EventContent()
    data class Receipt(val messageTimetoken: String, val type: String = "receipt") : EventContent()
    data class Mention(val messageTimetoken: String, val channel: String, val type: String = "mention") : EventContent()
    data class Custom(val data: Any, val type: String = "custom") : EventContent()
    data class TextMessageContent(
        val type: MessageType,
        val text: String,
        val files: List<File>? = null,
    ) : EventContent()
}

//enum class EventType {
//    TYPING, REPORT, RECEIPT, MENTION, CUSTOM
//}

enum class EmitEventMethod{
    SIGNAL, PUBLISH;
}