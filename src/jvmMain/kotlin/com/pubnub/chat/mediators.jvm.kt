package com.pubnub.chat

import com.pubnub.api.PubNub
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.PUBNUB_CHAT_VERSION
import com.pubnub.kmp.PNFuture

/**
 * Initializes an instance of [Chat] with the specified [ChatConfiguration] and [PNConfiguration].
 *
 * @param chatConfiguration The configuration for initializing the [Chat] instance.
 * @param pubNubConfiguration The base PubNub configuration to be used.
 * @return A [PNFuture] containing the initialized [Chat] instance.
 */
fun Chat.Companion.init(chatConfiguration: ChatConfiguration, pubNubConfiguration: PNConfiguration): PNFuture<Chat> {
    val builder = PNConfiguration.builder(pubNubConfiguration)
    builder.pnsdkSuffixes = pubNubConfiguration.pnsdkSuffixes.toMutableMap().apply {
        put("chat-sdk", "CA-JVM/$PUBNUB_CHAT_VERSION")
    }
    val pubNub = PubNub.create(builder.build())

    return ChatImpl(chatConfiguration, pubNub).initialize()
}
