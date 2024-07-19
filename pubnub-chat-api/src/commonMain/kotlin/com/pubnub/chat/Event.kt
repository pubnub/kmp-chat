package com.pubnub.chat

import com.pubnub.chat.types.EventContent

interface Event<T : EventContent> {
    val chat: Chat
    val timetoken: Long
    val payload: T
    val channelId: String
    val userId: String
}
