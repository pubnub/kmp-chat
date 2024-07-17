package com.pubnub.chat

import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.PubNub

fun Chat.Companion.init(chatConfiguration: ChatConfiguration, pubnub: PubNub): PNFuture<Chat> {
    return ChatImpl(chatConfiguration, pubnub).initialize()
}
