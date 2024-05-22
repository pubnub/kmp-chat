package com.pubnub.kmp

import com.pubnub.kmp.types.MessageActions
import com.pubnub.kmp.types.TextMessageContent

data class Message(
    private val chat: Chat,
    val timetoken: String,
    val content: TextMessageContent,
    val channelId: String,
    val userId: String,
    val actions: MessageActions? = null,
    val meta: Map<String, Any>? = null
){

}