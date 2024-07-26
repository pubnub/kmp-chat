package com.pubnub.chat

import com.pubnub.api.UserId
import com.pubnub.api.enums.PNLogVerbosity
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.PUBNUB_CHAT_VERSION
import com.pubnub.internal.v2.PNConfigurationImpl
import com.pubnub.kmp.PNFuture

fun Chat.Companion.init(chatConfiguration: ChatConfiguration, pubnubConfiguration: PNConfiguration): PNFuture<Chat> {
    val builder = PNConfigurationImpl.Builder(pubnubConfiguration)
    builder.pnsdkSuffixes = pubnubConfiguration.pnsdkSuffixes.toMutableMap().apply {
        put("chat-sdk", "Chat-JVM/$PUBNUB_CHAT_VERSION")
    }
    return ChatImpl(chatConfiguration, com.pubnub.api.PubNub.create(builder.build())).initialize()
}

fun main() {
    val pnConfiguration = PNConfiguration.builder(UserId("demo"), "demo").apply {
        logVerbosity = PNLogVerbosity.BODY
        publishKey = "demo"
    }.build()
    Chat.init(ChatConfiguration(),pnConfiguration).async {

    }
}