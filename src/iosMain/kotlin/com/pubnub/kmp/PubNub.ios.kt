@file:OptIn(ExperimentalForeignApi::class)

package com.pubnub.kmp

//import cocoapods.PubNub.PNConfiguration.Companion.configurationWithPublishKey
//import cocoapods.PubNub.PNPublishStatus
//import cocoapods.PubNub.PubNub
//import cocoapods.PubNub.publish
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.uint64_t
//import pubnubobjc.PubNubObjC
import cocoapods.PubNubSwift.PubNubObjC

actual class PubNub actual constructor(configuration: PNConfiguration) {

    private val pubNub: PubNubObjC = PubNubObjC(configuration.userId.value, configuration.subscribeKey, configuration.publishKey)
//    private val pubNub: PubNub = PubNub.clientWithConfiguration(
//        cocoapods.PubNub.PNConfiguration().apply {
//            publishKey = configuration.publishKey
//            subscribeKey = configuration.subscribeKey
//            setUUID(configuration.userId.value)
//        }
//        configurationWithPublishKey(configuration.publishKey, configuration.subscribeKey, uuid = configuration.userId.value)
//    )

    @OptIn(ExperimentalForeignApi::class)
    actual fun publish(
        channel: String,
        message: Any,
        meta: Any?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?
    ): Endpoint<PNPublishResult> {
        println("HELLO Publishing")
        return object : Endpoint<PNPublishResult> {
            override fun async(callback: (Result<PNPublishResult>) -> Unit) {
                println("HELLO asyncing")
                pubNub.publishWithChannel(channel, message) { uLong: uint64_t ->
                    println("HELLO callbacking")
                    callback(Result.success(PNPublishResult(uLong.toLong()))) }
//                { result: PNPublishStatus? ->
//                    if (result != null) {
//                        callback(PNPublishResult(result.data().timetoken.longValue))
//                    }
//                }
            }
        }
    }

    actual fun setUserMetadata(
        uuid: String?,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: Any?,
        includeCustom: Boolean,
        type: String?,
        status: String?
    ): Endpoint<PNUUIDMetadataResult> {
        TODO("Not yet implemented")
    }

    actual fun removeUserMetadata(uuid: String?): Endpoint<PNRemoveMetadataResult> {
        TODO("Not yet implemented")
    }

    actual fun getUserMetadata(
        uuid: String?,
        includeCustom: Boolean
    ): Endpoint<PNUUIDMetadataResult> {
        TODO("Not yet implemented")
    }
}

actual class PNPublishResult(
    actual val timetoken: Long
)