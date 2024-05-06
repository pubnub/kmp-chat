@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.pubnub.kmp

import com.pubnub.kmp.models.consumers.objects.PNKey
import com.pubnub.kmp.models.consumers.objects.PNMembershipKey
import com.pubnub.kmp.models.consumers.objects.PNPage
import com.pubnub.kmp.models.consumers.objects.PNSortKey
import com.pubnub.kmp.models.consumers.objects.channel.PNChannelMetadataResult
import com.pubnub.kmp.models.consumers.objects.memberships.PNChannelDetailsLevel

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
    ): Endpoint<PNUserMetadataResult>

    fun removeUserMetadata(uuid: String? = null): Endpoint<PNRemoveMetadataResult>
    fun getUserMetadata(uuid: String?, includeCustom: Boolean): Endpoint<PNUserMetadataResult>

    fun getAllUserMetadata(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        includeCount: Boolean = false,
        includeCustom: Boolean = false,
    ): Endpoint<PNUserMetadataArrayResult>

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

expect class PNPublishResult {
    val timetoken: Long
}

expect class PNUUIDMetadata {
    val id: String
    val name: String?
    val externalId: String?
    val profileUrl: String?
    val email: String?
    val custom: Any?
    val updated: String?
    val eTag: String?
    val type: String?
    val status: String?
}

expect class PNUserMetadataResult {
    val status: Int
    val data: PNUUIDMetadata?
}

expect class PNRemoveMetadataResult {
    val status: Int
}

expect class PNUserMetadataArrayResult {
    val status: Int
    val data: Collection<PNUUIDMetadata>
    val totalCount: Int?
    val next: PNPage.PNNext?
    val prev: PNPage.PNPrev?
}


