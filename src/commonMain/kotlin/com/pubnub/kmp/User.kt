package com.pubnub.kmp

import com.pubnub.api.CustomObject
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
        updated: String? = null, // todo do we need this?
        callback: (Result<User>) -> Unit) {
        return chat.updateUser(id, name, externalId, profileUrl, email,
            custom, status, type, updated, callback)
    }

    fun delete(softDelete: Boolean, callback: (Result<User>) -> Unit) {
        return chat.deleteUser(id, softDelete, callback )
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


//import kotlin.js.JsExport
//
//@JsExport
//interface UserInfo {
//    val id: String
//    val name: String?
//    val externalId: String?
//    val profileUrl: String?
//    val email: String?
//    val custom: Map<String, Any?>?
//    val status: String?
//    val type: String?
//    val updated: String?
//    val lastActiveTimestamp: Long?
//}
//
//data class User(
//    val chat: Chat,
//    override val id: String,
//    override val name: String? = null,
//    override val externalId: String? = null,
//    override val profileUrl: String? = null,
//    override val email: String? = null,
//    override val custom: Map<String, Any?>? = null,
//    override val status: String? = null,
//    override val type: String? = null,
//    override val updated: String? = null,
//    override val lastActiveTimestamp: Long? = null
//) : UserInfo {
//    fun update(
//        name: String? = null,
//        externalId: String? = null,
//        profileUrl: String? = null,
//        email: String? = null,
//        custom: Map<String, Any?>? = null,
//        status: String? = null,
//        type: String? = null,
//        updated: String? = null, // todo do we need this?
//        callback: (Result<User>) -> Unit) {
//        return chat.updateUser(id, name, externalId, profileUrl, email, custom, status, type, updated, callback)
//    }
//
//    fun delete(softDelete: Boolean, callback: (Result<User>) -> Unit) {
//        return chat.deleteUser(id, softDelete, callback )
//    }
//}

//v# update user
//v# delete user
//# create user from server response object
//# streamUpdatesOn
//# streamUpdates
//# wherePresent()
//# isPresentOn
//# getMemberships
//# report
