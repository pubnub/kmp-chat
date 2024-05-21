package com.pubnub.kmp

import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.v2.callbacks.Result
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
    val custom: CustomObject? = null,
    val status: String? = null,
    val type: String? = null,
    val updated: String? = null,
    val lastActiveTimestamp: Long? = null
) {
    fun update(
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
        callback: (Result<User>) -> Unit
    ) {
        return chat.updateUser(
            id, name, externalId, profileUrl, email,
            custom, status, type, callback
        )
    }

    fun delete(soft: Boolean = false, callback: (Result<User>) -> Unit) {
        return chat.deleteUser(id, soft, callback)
    }

    fun wherePresent(callback: (Result<List<String>>) -> Unit) {
        return chat.wherePresent(id, callback)
    }

    fun isPresentOn(channelId: String, callback: (Result<Boolean>) -> Unit) {
        return chat.isPresent(id, channelId, callback)
    }

    fun getMemberships(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
        callback: (Result<MembershipsResponse>) -> Unit
    ) {
        val includeParameters = IncludeParameters()

        chat.pubNub.getMemberships(
            uuid = id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = includeParameters.totalCount,
            includeCustom = includeParameters.customFields,
            includeChannelDetails = getChannelDetailsType(includeParameters.customChannelFields)
        ).async { result: Result<PNChannelMembershipArrayResult> ->
            result.onSuccess { pnChannelMembershipArrayResult ->
                val membershipsResponse = MembershipsResponse(
                    next = pnChannelMembershipArrayResult.next,
                    prev = pnChannelMembershipArrayResult.prev,
                    total = pnChannelMembershipArrayResult.totalCount ?: 0,
                    status = pnChannelMembershipArrayResult.status.toString(),
                    memberships = getMembershipsFromResult(pnChannelMembershipArrayResult, this)
                )
                callback(Result.success(membershipsResponse))
            }.onFailure { error ->
                callback(Result.failure(Exception("Failed to retrieve getMembership data: ${error.message}")))
            }

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
        user: User
    ): List<Membership> {
        val memberships: List<Membership> =
            pnChannelMembershipArrayResult.data.map { pnChannelMembership: PNChannelMembership ->
                Membership(
                    channel = getChannel(pnChannelMembership),
                    user = user,
                    custom = pnChannelMembership.custom,
                )
            }
        return memberships
    }

    private fun getChannel(pnChannelMembership: PNChannelMembership): Channel {
        return Channel(
            chat = chat,
            id = pnChannelMembership.channel?.id ?: "undefined", //todo not sure about this
            name = pnChannelMembership.channel?.name,
            custom = pnChannelMembership.custom?.let { createCustomObject(it) },
            description = pnChannelMembership.channel?.description,
            updated = pnChannelMembership.channel?.updated,
            status = pnChannelMembership.channel?.status,
            type = ChannelType.DIRECT, //todo not sure about this
        )
    }

}

// todo
//v# update user
//v# delete user
//v# wherePresent()
//v# isPresentOn
//v# getMemberships
//# create user from server response object
//# streamUpdatesOn
//# streamUpdates
//# report
