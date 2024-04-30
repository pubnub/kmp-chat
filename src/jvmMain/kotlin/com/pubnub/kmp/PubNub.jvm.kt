@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.pubnub.kmp

import com.pubnub.api.Endpoint
import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.v2.callbacks.Result
import java.util.function.Consumer

actual class PubNub actual constructor(configuration: PNConfiguration) {

    private val pubNub: PubNub = PubNub.create(
        com.pubnub.api.v2.PNConfiguration.builder(UserId(configuration.userId.value), configuration.subscribeKey).apply {
            publishKey = configuration.publishKey
        }.build()
    )

    actual fun publish(
        channel: String,
        message: Any,
        meta: Any?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?
    ): com.pubnub.kmp.Endpoint<PNPublishResult> {
        return pubNub.publish(channel, message, meta, shouldStore, usePost, replicate, ttl).toKmp()
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
        status: String?,
    ): com.pubnub.kmp.Endpoint<PNUUIDMetadataResult> {
        return pubNub.setUUIDMetadata(uuid, name, externalId, profileUrl, email, custom, includeCustom, type, status).toKmp()
    }

    // todo
    actual fun removeUserMetadata(uuid: String?): com.pubnub.kmp.Endpoint<PNRemoveMetadataResult>{
        return pubNub.removeUUIDMetadata(uuid).toKmp()
    }

    // todo
    actual fun getUserMetadata(uuid: String?, includeCustom: Boolean): com.pubnub.kmp.Endpoint<PNUUIDMetadataResult>{
        return pubNub.getUUIDMetadata(uuid, includeCustom).toKmp()
    }
}

private fun <T> Endpoint<T>.toKmp() : com.pubnub.kmp.Endpoint<T>{
    return object : com.pubnub.kmp.Endpoint<T> {
        override fun async(callback: (kotlin.Result<T>) -> Unit) {
            async(Consumer<Result<T>> { result ->
                result.onSuccess { callback(kotlin.Result.success(it)) }
                    .onFailure{callback(kotlin.Result.failure(it))}
            })
        }
    }
}

actual typealias PNPublishResult = PNPublishResult

actual typealias PNUUIDMetadata = PNUUIDMetadata

actual typealias PNUUIDMetadataResult = com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult

actual typealias PNRemoveMetadataResult = com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
