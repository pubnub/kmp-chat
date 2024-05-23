package com.pubnub.kmp.types

data class TextMessageContent(
    val type: MessageType = MessageType.TEXT,
    val text: String,
    val files: List<File>? = null
) {
}

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