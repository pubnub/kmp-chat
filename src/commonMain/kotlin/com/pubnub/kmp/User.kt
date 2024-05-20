package com.pubnub.kmp

import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.membership.IncludeParameters
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
        callback: (Result<User>) -> Unit) {
        return chat.updateUser(id, name, externalId, profileUrl, email,
            custom, status, type, callback)
    }

    fun delete(soft: Boolean = false, callback: (Result<User>) -> Unit) {
        return chat.deleteUser(id, soft, callback )
    }

    fun wherePresent(callback: (Result<List<String>>) -> Unit){
        return chat.wherePresent(id, callback)
    }

    fun isPresentOn(channelId: String, callback: (Result<Boolean>) -> Unit){
        return chat.isPresent(id, channelId, callback)
    }

    fun getMemberships(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
        callback: (kotlin.Result<MembershipsResponse>) -> Unit
    ) {
        chat.getMembership(this, limit, page, filter, sort, IncludeParameters(), callback)
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
