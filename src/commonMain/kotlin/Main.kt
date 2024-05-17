package com.pubnub

import com.pubnub.kmp.PNConfiguration
import com.pubnub.kmp.PNPublishResult
import com.pubnub.kmp.PubNub
import com.pubnub.kmp.UserId

fun main() {
    println("Hello World!")
    val config = PNConfiguration(UserId("demo"), "demo", "demo")
    val pubnub = PubNub(config)
    pubnub.publish("testChannel", "testMessage").async { it: PNPublishResult ->
        println(it.timetoken)
    }
}