package com.pubnub.chat.internal

import com.pubnub.chat.Chat
import com.pubnub.chat.User
import com.pubnub.kmp.PNFuture

interface ChatInternal : Chat {
    val editMessageActionName: String
    val deleteMessageActionName: String

    fun createUser(user: User): PNFuture<User>
}
