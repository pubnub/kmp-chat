package com.pubnub.kmp

import com.pubnub.kmp.Chat

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
    val lastActiveTimestamp: Long? = null
) {
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
