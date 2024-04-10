//@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
//
//package com.pubnub.kmp
//
//import java.util.function.Consumer
//
//actual class PubNub actual constructor(configuration: PNConfiguration) {
//
//    private val pubNub: PubNub = PubNub.create(
//        com.pubnub.api.v2.PNConfiguration.builder(UserId(configuration.userId.value), configuration.subscribeKey).apply {
//            publishKey = configuration.publishKey
//        }.build()
//    )
//
//    actual fun publish(
//        channel: String,
//        message: Any,
//        meta: Any?,
//        shouldStore: Boolean?,
//        usePost: Boolean,
//        replicate: Boolean,
//        ttl: Int?
//    ): Endpoint<PNPublishResult> {
//        return pubNub.publish(channel, message, meta, shouldStore, usePost, replicate, ttl).toKmp()
//    }
//}
//
//private fun <T> com.pubnub.api.Endpoint<T>.toKmp() : Endpoint<T>{
//    return object : Endpoint<T> {
//        override fun async(callback: (T) -> Unit) {
//           async(Consumer<Result<T>> { result -> result.onSuccess{ callback(it) } })
//        }
//    }
//}
//
//actual typealias PNPublishResult = PNPublishResult