package com.pubnub.kmp

import com.pubnub.kmp.Chat
import kotlin.js.JsExport

@JsExport
interface UserInfo {
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
}

data class User(
    val chat: Chat,
    override val id: String,
    override val name: String? = null,
    override val externalId: String? = null,
    override val profileUrl: String? = null,
    override val email: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val status: String? = null,
    override val type: String? = null,
    override val updated: String? = null,
    override val lastActiveTimestamp: Long? = null
) : UserInfo {
    fun update(
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: Map<String, Any?>? = null,
        status: String? = null,
        type: String? = null,
        updated: String? = null, // todo do we need this?
        callback: (Result<User>) -> Unit) {
        return chat.updateUser(id, name, externalId, profileUrl, email, custom, status, type, updated, callback)
    }

    fun delete(softDelete: Boolean, callback: (Result<User>) -> Unit) {
        return chat.deleteUser(id, softDelete, callback )
    }
}

//v# update user
//v# delete user
//# create user from server response object
//# streamUpdatesOn
//# streamUpdates
//# wherePresent()
//# isPresentOn
//# getMemberships
//# report


//
//interface ObjectCustom : Map<String, Any>
