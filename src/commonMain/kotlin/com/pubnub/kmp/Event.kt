package com.pubnub.kmp

import com.pubnub.kmp.types.EventContent

data class Event<T: EventContent>(
    val chat: Chat,
    val timetoken: Long,
    val payload: T,
    val channelId: String,
    val userId: String
    //może dodać type?
)
data class ListenerMessage(
    val type: String,
    val content: Any // todo
)


// todo use classes from Kotlin
interface ListenerEvent{
    val channel: String
    val message: EventContent
    val timetoken: Long
    val publisher: String
}

data class MessageEvent(
    override val channel: String,
    val subscription: String,
    override val timetoken: Long,
    override val message: EventContent,
    override val publisher: String,
) : ListenerEvent

data class SignalEvent(
    override val channel: String,
    val subscription: String,
    override val timetoken: Long,
    override val message: EventContent,
    override val publisher: String,
) : ListenerEvent