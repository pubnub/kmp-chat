package com.pubnub.com.pubnub.kmp

import com.pubnub.kmp.User

interface Chat {
    fun createUser(
        id: String,
        name: String?,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: Any? = null,
        status: String? = null,
        type: String? = null,
        callback: (Result<User>) -> Unit,
    )

    fun updateUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: Any?,
        status: String?,
        type: String?,
        updated: String?, //todo do we need this?
        callback: (Result<User>) -> Unit
    )

    fun deleteUser(id: String, softDelete: Boolean, callback: (Result<User>) -> Unit)
}