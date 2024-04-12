//@file:OptIn(ExperimentalForeignApi::class, ExperimentalForeignApi::class)
//
//package com.pubnub.kmp
//
//import cocoapods.PubNub.PNConfiguration.Companion.configurationWithPublishKey
//import cocoapods.PubNub.PNPublishStatus
//import cocoapods.PubNub.PubNub
//import cocoapods.PubNub.publish
//import kotlinx.cinterop.ExperimentalForeignApi
//import platform.posix.uint64_t
//import pubnubobjc.PubNubObjC
//
//actual class PubNub actual constructor(configuration: PNConfiguration) {
//
//    private val pubNub: PubNubObjC = PubNubObjC("a", "b", "c")
////    private val pubNub: PubNub = PubNub.clientWithConfiguration(
////        cocoapods.PubNub.PNConfiguration().apply {
////            publishKey = configuration.publishKey
////            subscribeKey = configuration.subscribeKey
////            setUUID(configuration.userId.value)
////        }
////        configurationWithPublishKey(configuration.publishKey, configuration.subscribeKey, uuid = configuration.userId.value)
////    )
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
//
//        return object : Endpoint<PNPublishResult> {
//            override fun async(callback: (PNPublishResult) -> Unit) {
//                pubNub.publishWithChannel(channel, message) { uLong: uint64_t -> callback(PNPublishResult(uLong.toLong())) }
////                { result: PNPublishStatus? ->
////                    if (result != null) {
////                        callback(PNPublishResult(result.data().timetoken.longValue))
////                    }
////                }
//            }
//        }
//    }
//}
//
//actual class PNPublishResult(
//    actual val timetoken: Long
//)
