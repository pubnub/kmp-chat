@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import createPubNub
import kotlin.js.Promise
import PubNub as PubNubJs

actual class PubNub actual constructor(configuration: PNConfiguration) {

    private val jsPubNub: PubNubJs = createPubNub(configuration.userId.value, configuration.subscribeKey, configuration.publishKey)

    actual fun publish(
        channel: String,
        message: Any,
        meta: Any?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?
    ): Endpoint<PNPublishResult> {
        return object : Endpoint<PNPublishResult> {
            override fun async(callback: (Result<PNPublishResult>) -> Unit) {
                val params = Any().asDynamic()
                params.message = "myMessage"
                params.channel = "myChannel"
                jsPubNub.publish(params).then(onFulfilled = { it: dynamic ->
                    callback(Result.success(PNPublishResult(it.timetoken.toString().toLong())))
                }, onRejected = { it: Throwable ->
                    callback(Result.failure(it))
                })
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
        return object : Endpoint<PNUUIDMetadataResult> {
            override fun async(callback: (Result<PNUUIDMetadataResult>) -> Unit) {
                val params = Any().asDynamic()
                params.uuid = uuid
                params.data = Any().asDynamic()
                with (params.data) {
                    this.name = name
                    this.externalId = externalId
                    this.profileUrl = profileUrl
                    this.email = email
                    this.custom = custom
                }
                params.include = Any().asDynamic()
                params.include.customFields = includeCustom

                jsPubNub.objects.setUUIDMetadata(params).then(onFulfilled = { it: dynamic ->
                    callback(Result.success(it))
                }, onRejected = { it: Throwable ->
                    callback(Result.failure(it))
                })
            }
        }
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

private fun <T> Promise<T>.toKmp() : Endpoint<T>{
    return object : Endpoint<T> {
        override fun async(callback: (Result<T>) -> Unit) {
            then { callback(Result.success(it))}
        }
    }
}
