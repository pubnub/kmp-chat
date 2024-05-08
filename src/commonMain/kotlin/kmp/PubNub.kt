@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.pubnub.kmp

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult

interface Endpoint<T> {
    fun async(callback: (Result<T>)-> Unit)
}

expect class PubNub(configuration: PNConfiguration) {
    fun publish(channel: String,
                message: Any,
                meta: Any? = null,
                shouldStore: Boolean? = true,
                usePost: Boolean = false,
                replicate: Boolean = true,
                ttl: Int? = null,
    ): Endpoint<PNPublishResult>


    fun setUserMetadata(
        uuid: String? = null,
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: Map<String,Any?>? = null,
        includeCustom: Boolean = false,
        type: String? = null,
        status: String? = null,
    ): Endpoint<PNUUIDMetadataResult>

    fun removeUserMetadata(uuid: String? = null): Endpoint<PNRemoveMetadataResult>
    fun getUserMetadata(uuid: String?, includeCustom: Boolean): Endpoint<PNUUIDMetadataResult>

    fun getAllUserMetadata(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        includeCount: Boolean = false,
        includeCustom: Boolean = false,
    ): Endpoint<PNUUIDMetadataArrayResult>

    fun getAllChannelMetadata(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        includeCount: Boolean = false,
        includeCustom: Boolean = false,
    ): Endpoint<Unit>

    /**
     * Returns metadata for the specified Channel, optionally including the custom data object for each.
     *
     * @param channel Channel name.
     * @param includeCustom Include respective additional fields in the response.
     */
    fun getChannelMetadata(
        channel: String,
        includeCustom: Boolean = false,
    ): Endpoint<PNChannelMetadataResult>

    /**
     * Set metadata for a Channel in the database, optionally including the custom data object for each.
     *
     * @param channel Channel name.
     * @param name Name of a channel.
     * @param description Description of a channel.
     * @param custom Object with supported data types.
     * @param includeCustom Include respective additional fields in the response.
     */
    fun setChannelMetadata(
        channel: String,
        name: String? = null,
        description: String? = null,
        custom: Any? = null,
        includeCustom: Boolean = false,
        type: String? = null,
        status: String? = null,
    ): Endpoint<PNChannelMetadataResult>

    /**
     * Removes the metadata from a specified channel.
     *
     * @param channel Channel name.
     */
    fun removeChannelMetadata(channel: String): Endpoint<PNRemoveMetadataResult>

//    fun getMemberships(
//        uuid: String? = null,
//        limit: Int? = null,
//        page: PNPage? = null,
//        filter: String? = null,
//        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
//        includeCount: Boolean = false,
//        includeCustom: Boolean = false,
//        includeChannelDetails: PNChannelDetailsLevel? = null,
//    ): Endpoint<PNChannelMembershipArrayResult>

}
