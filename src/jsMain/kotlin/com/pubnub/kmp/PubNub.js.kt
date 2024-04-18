@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import Optional
import createPubNub
import toOptional
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
                val params = object : PubNubJs.PublishParameters {
                    override var message: Any = "myMessage"
                    override var channel: String = "myChannel"
                }
                jsPubNub.publish(params).then(onFulfilled = { it: PubNubJs.PublishResponse ->
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
                val params = object : PubNubJs.SetUUIDMetadataParameters<PubNubJs.ObjectCustom> {
                    override var data: PubNubJs.UUIDMetadata<PubNubJs.ObjectCustom> = UUIDMetadata(
                        name.toOptional(), externalId.toOptional(), profileUrl.toOptional(), email.toOptional(), status.toOptional(), type.toOptional()
                    )

                    override var uuid: String? = uuid

                    override var include: PubNubJs.`T$30`? = object : PubNubJs.`T$30` {
                        override var customFields: Boolean? = includeCustom
                    }
                }

                jsPubNub.objects.setUUIDMetadata<dynamic>(params).then(onFulfilled = { it: dynamic ->
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

fun UUIDMetadata(
    name: Optional<String?>,
    externalId: Optional<String?>,
    profileUrl: Optional<String?>,
    email: Optional<String?>,
    status: Optional<String?>,
    type: Optional<String?>,
) : PubNubJs.UUIDMetadata<dynamic> {
    val result = Any().asDynamic()
    name.asValue()?.let { result.name = it.value }
    externalId.asValue()?.let { result.externalId = it.value }
    profileUrl.asValue()?.let { result.profileUrl = it.value }
    email.asValue()?.let { result.email = it.value }
    status.asValue()?.let { result.status = it.value }
    type.asValue()?.let { result.type = it.value }
    return result
}