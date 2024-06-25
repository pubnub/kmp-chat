package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA
import com.pubnub.kmp.membership.IncludeParameters
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.membership.MembershipsResponse

data class User(
    val chat: Chat,
    val id: String,
    val name: String? = null,
    val externalId: String? = null,
    val profileUrl: String? = null,
    val email: String? = null,
    val custom: Map<String, Any?>? = null,
    val status: String? = null,
    val type: String? = null,
    val updated: String? = null,
    val lastActiveTimestamp: Long? = null,
) {
    fun update(
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
    ): PNFuture<User> {
        return chat.updateUser(
            id, name, externalId, profileUrl, email,
            custom, status, type
        )
    }

    fun delete(soft: Boolean = false): PNFuture<User> {
        return chat.deleteUser(id, soft)
    }

    fun wherePresent(): PNFuture<List<String>> {
        return chat.wherePresent(id)
    }

    fun isPresentOn(channelId: String): PNFuture<Boolean> {
        return chat.isPresent(id, channelId)
    }

    fun getMemberships(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<MembershipsResponse> {
        val includeParameters = IncludeParameters()

        return chat.pubNub.getMemberships(
            uuid = id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = includeParameters.totalCount,
            includeCustom = includeParameters.customFields,
            includeChannelDetails = getChannelDetailsType(includeParameters.customChannelFields)
        ).then { pnChannelMembershipArrayResult ->
            MembershipsResponse(
                next = pnChannelMembershipArrayResult.next,
                prev = pnChannelMembershipArrayResult.prev,
                total = pnChannelMembershipArrayResult.totalCount ?: 0,
                status = pnChannelMembershipArrayResult.status,
                memberships = getMembershipsFromResult(pnChannelMembershipArrayResult, this).toSet()
            )
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA.message, exception))
        }
    }

    private fun getChannelDetailsType(includeChannelWithCustom: Boolean): PNChannelDetailsLevel {
        return if (includeChannelWithCustom) {
            PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM
        } else {
            PNChannelDetailsLevel.CHANNEL
        }
    }

    private fun getMembershipsFromResult(
        pnChannelMembershipArrayResult: PNChannelMembershipArrayResult,
        user: User,
    ): List<Membership> {
        val memberships: List<Membership> =
            pnChannelMembershipArrayResult.data.map { pnChannelMembership: PNChannelMembership ->
                Membership.fromMembershipDTO(chat, pnChannelMembership, user)
            }
        return memberships
    }

    internal val uuidFilterString = "uuid.id == '${this.id}'"

    companion object {
        internal fun fromDTO(chat: Chat, user: PNUUIDMetadata): User = User(
            chat,
            id = user.id,
            name = user.name,
            externalId = user.externalId,
            profileUrl = user.profileUrl,
            email = user.email,
            custom = user.custom,
            updated = user.updated,
            status = user.status,
            type = user.type,
            lastActiveTimestamp = (user.custom?.get("lastActiveTimestamp") as? Number)?.toLong()
        )
    }

}
