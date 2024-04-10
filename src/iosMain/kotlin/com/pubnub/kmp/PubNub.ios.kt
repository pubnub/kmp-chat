package com.pubnub.kmp

//import cocoapods.PubNubSwift.*

actual class PubNub actual constructor(configuration: PNConfiguration) {
    actual fun publish(
        channel: String,
        message: Any,
        meta: Any?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?
    ): Endpoint<PNPublishResult> {
        println("HELLO endpoint")
        return object : Endpoint<PNPublishResult> {
            override fun async(callback: (PNPublishResult) -> Unit) {
                callback(PNPublishResult(123L))
            }
        }
    }
}

actual class PNPublishResult(
    actual val timetoken: Long
)
