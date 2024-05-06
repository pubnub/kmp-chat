@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.pubnub.kmp

import com.pubnub.api.Endpoint
import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.PNConfiguration
import com.pubnub.kmp.models.consumers.objects.channel.PNChannelMetadataResult
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
        custom: Map<String, Any?>?,
        includeCustom: Boolean,
        type: String?,
        status: String?,
    ): com.pubnub.kmp.Endpoint<PNUserMetadataResult> {
        return pubNub.setUUIDMetadata(uuid, name, externalId, profileUrl, email, custom, includeCustom, type, status).toKmp()
    }

    // todo
    actual fun removeUserMetadata(uuid: String?): com.pubnub.kmp.Endpoint<PNRemoveMetadataResult> {
        return pubNub.removeUUIDMetadata(uuid).toKmp()
    }

    // todo
    actual fun getUserMetadata(uuid: String?, includeCustom: Boolean): com.pubnub.kmp.Endpoint<PNUserMetadataResult> {
        return pubNub.getUUIDMetadata(uuid, includeCustom).toKmp()
    }

    actual fun getAllUserMetadata(
        limit: Int?,
        page: com.pubnub.kmp.models.consumers.objects.PNPage?,
        filter: String?,
        sort: Collection<com.pubnub.kmp.models.consumers.objects.PNSortKey<com.pubnub.kmp.models.consumers.objects.PNKey>>,
        includeCount: Boolean,
        includeCustom: Boolean
    ): com.pubnub.kmp.Endpoint<PNUserMetadataArrayResult> {
        TODO("Not yet implemented")
    }

    actual fun getAllChannelMetadata(
        limit: kotlin.Int?,
        page: com.pubnub.kmp.models.consumers.objects.PNPage?,
        filter: kotlin.String?,
        sort: kotlin.collections.Collection<com.pubnub.kmp.models.consumers.objects.PNSortKey<com.pubnub.kmp.models.consumers.objects.PNKey>>,
        includeCount: kotlin.Boolean,
        includeCustom: kotlin.Boolean
    ): com.pubnub.kmp.Endpoint<Unit> {
        TODO("Not yet implemented")
    }

    /**
     * Returns metadata for the specified Channel, optionally including the custom data object for each.
     *
     * @param channel Channel name.
     * @param includeCustom Include respective additional fields in the response.
     */
    actual fun getChannelMetadata(
        channel: String,
        includeCustom: Boolean
    ): com.pubnub.kmp.Endpoint<PNChannelMetadataResult> {
        TODO("Not yet implemented")
    }

    /**
     * Set metadata for a Channel in the database, optionally including the custom data object for each.
     *
     * @param channel Channel name.
     * @param name Name of a channel.
     * @param description Description of a channel.
     * @param custom Object with supported data types.
     * @param includeCustom Include respective additional fields in the response.
     */
    actual fun setChannelMetadata(
        channel: String,
        name: String?,
        description: String?,
        custom: Any?,
        includeCustom: Boolean,
        type: String?,
        status: String?
    ): com.pubnub.kmp.Endpoint<PNChannelMetadataResult> {
        TODO("Not yet implemented")
    }

    /**
     * Removes the metadata from a specified channel.
     *
     * @param channel Channel name.
     */
    actual fun removeChannelMetadata(channel: String): com.pubnub.kmp.Endpoint<PNRemoveMetadataResult> {
        TODO("Not yet implemented")
    }
}

private fun <T> Endpoint<T>.toKmp() : com.pubnub.kmp.Endpoint<T> {
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

actual typealias PNUUIDMetadata = com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata

actual typealias PNUserMetadataResult = com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult

actual typealias PNRemoveMetadataResult = com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult

actual typealias PNUserMetadataArrayResult = com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
