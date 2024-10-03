package com.pubnub.internal

import ChatConfig
import ChatJs
import CustomPayloadsJs
import PushNotificationsConfig
import RateLimitPerChannelJs
import com.pubnub.api.UserId
import com.pubnub.api.v2.createPNConfiguration
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.EventImpl
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.createPubNub
import com.pubnub.kmp.toMap
import toJs
import kotlin.test.Test

internal actual val PLATFORM: String = "JS"

class Tests {
    @Test
    fun test() {
        val pn = createPubNub(createPNConfiguration(UserId("abc"), "abc", "abc"))
        val chat = ChatImpl(ChatConfiguration(), pn)
        val event = EventImpl(chat, 123L, EventContent.Mention(256L, "myChan"), "chanId1", "userId2")
        val chatJs = ChatJs(
            chat,
            object : ChatConfig {
                override val saveDebugLog: Boolean
                    get() = TODO("Not yet implemented")
                override val typingTimeout: Int
                    get() = TODO("Not yet implemented")
                override val storeUserActivityInterval: Int
                    get() = TODO("Not yet implemented")
                override val storeUserActivityTimestamps: Boolean
                    get() = TODO("Not yet implemented")
                override val pushNotifications: PushNotificationsConfig
                    get() = TODO("Not yet implemented")
                override val rateLimitFactor: Int
                    get() = TODO("Not yet implemented")
                override val rateLimitPerChannel: RateLimitPerChannelJs
                    get() = TODO("Not yet implemented")
                override val errorLogger: Any?
                    get() = TODO("Not yet implemented")
                override val customPayloads: CustomPayloadsJs
                    get() = TODO("Not yet implemented")
            }
        )
        val eventJs = event.toJs(chatJs)
        println(eventJs)

        eventJs.unsafeCast<JsMap<Any>>().toMap().forEach {
            println("${it.key}: ${it.value}")
            it.value.unsafeCast<JsMap<Any>>().toMap().forEach {
                println("    ${it.key}: ${it.value}")
            }
        }
    }
}
