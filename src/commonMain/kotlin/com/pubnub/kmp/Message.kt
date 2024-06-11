package com.pubnub.kmp

import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.kmp.types.EventContent

data class Message(
    private val chat: Chat,
    val timetoken: Long,
    val content: EventContent.TextMessageContent,
    val channelId: String,
    val userId: String,
    val actions: Map<String, Map<String, List<Action>>>? = null,
    val meta: Map<String, Any>? = null
) {
    val text: String
        get() = content.text // todo implement Message.text() method from TS
}
