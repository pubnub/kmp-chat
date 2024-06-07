package com.pubnub.kmp

import com.pubnub.kmp.types.EventContent

data class Event<T: EventContent>(
    val chat: Chat,
    val timetoken: Long,
    val payload: T,
    val channelId: String,
    val userId: String
)