package com.pubnub.kmp

import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.MessageActions

data class Message(
    private val chat: Chat,
    val timetoken: Long,
    val content: EventContent.TextMessageContent,
    val channelId: String,
    val userId: String,
    val actions: MessageActions? = null,
    val meta: Map<String, Any>? = null
){

}