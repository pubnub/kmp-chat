package com.pubnub.chat

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

interface User {
    val chat: Chat
    val id: String
    val name: String?
    val externalId: String?
    val profileUrl: String?
    val email: String?
    val custom: Map<String, Any?>?
    val status: String?
    val type: String?
    val updated: String?
    val lastActiveTimestamp: Long?

    fun update(
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
    ): PNFuture<User>

    fun delete(soft: Boolean = false): PNFuture<User>

    fun wherePresent(): PNFuture<List<String>>

    fun isPresentOn(channelId: String): PNFuture<Boolean>

    fun getMemberships(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<MembershipsResponse>

    fun setRestrictions(
        channel: Channel,
        ban: Boolean = false,
        mute: Boolean = false,
        reason: String? = null,
    ): PNFuture<Unit>

    fun getChannelRestrictions(channel: Channel): PNFuture<Restriction>

    fun getChannelsRestrictions(
        limit: Int? = null,
        page: PNPage? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<GetRestrictionsResponse>

    fun streamUpdates(callback: (user: User?) -> Unit): AutoCloseable

    fun active(): PNFuture<Boolean>

    fun report(reason: String): PNFuture<PNPublishResult>

    /**
     * Get a new `User` instance that is a copy of this `User` with its properties updated with information coming from `update`.
     */
    operator fun plus(update: PNUUIDMetadata): User

    companion object
}
