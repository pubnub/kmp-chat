package com.pubnub.kmp

import com.pubnub.api.CustomObject
import com.pubnub.api.v2.callbacks.Result

interface Chat {
    fun createUser(
        id: String,
        name: String?,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
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
        custom: CustomObject?,
        status: String?,
        type: String?,
        updated: String?, //todo do we need this?
        callback: (Result<User>) -> Unit
    )

    fun deleteUser(id: String, softDelete: Boolean = false, callback: (Result<User>) -> Unit)

    fun wherePresent(id: String, callback: (Result<List<String>>) -> Unit)

    fun isPresent(id: String, channel: String, callback: (Result<Boolean>) -> Unit)
}