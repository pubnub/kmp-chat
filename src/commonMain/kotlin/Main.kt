package com.pubnub

import com.pubnub.kmp.Chat
import com.pubnub.kmp.ChatConfig
import com.pubnub.kmp.PNConfiguration
import com.pubnub.kmp.PNPublishResult
import com.pubnub.kmp.PubNub
import com.pubnub.kmp.User
import com.pubnub.kmp.UserId

//fun main() {
//    println("Hello World!")
//    val config = PNConfiguration(UserId("demo"), "demo", "demo")
//    val pubnub = PubNub(config)
//    pubnub.publish("testChannel", "testMessage").async { it: Result<PNPublishResult> ->
//        it.onSuccess {
//            println(it.timetoken)
//        }
//    }
//}


fun main() {
    println("RESTARTING!")
    val chat = Chat(ChatConfig(PNConfiguration(UserId("uuuuuser"), "demo", "demo")))
    chat.createUser("idid", "mememe") { it: Result<User> ->
        it.onSuccess { println("SUCCSEESES") }
            .onFailure { println(it) }
    }
}