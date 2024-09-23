package com.pubnub.chat.internal

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.internal.timer.TimerManager
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

interface ChatInternal : Chat {
    val editMessageActionName: String
    val deleteMessageActionName: String
    val timerManager: TimerManager

    fun createUser(user: User): PNFuture<User>

    fun removeThreadChannel(
        chat: Chat,
        message: Message,
        soft: Boolean = false
    ): PNFuture<Pair<PNRemoveMessageActionResult, Channel>>

    fun restoreThreadChannel(message: Message): PNFuture<PNMessageAction?>

    fun createChannel(
        id: String,
        name: String? = null,
        description: String? = null,
        custom: CustomObject? = null,
        type: ChannelType? = null,
        status: String? = null,
    ): PNFuture<Channel>

    fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult>

    fun getThreadChannel(message: Message): PNFuture<ThreadChannel>
}
