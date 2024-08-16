package com.pubnub.chat.internal

import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.kmp.PNFuture

interface ChatInternal : Chat {
    val editMessageActionName: String
    val deleteMessageActionName: String

    fun createUser(user: User): PNFuture<User>

    fun removeThreadChannel(
        chat: Chat,
        message: Message,
        soft: Boolean = false
    ): PNFuture<Pair<PNRemoveMessageActionResult, Channel>>

    fun restoreThreadChannel(message: Message): PNFuture<PNMessageAction?>
}
