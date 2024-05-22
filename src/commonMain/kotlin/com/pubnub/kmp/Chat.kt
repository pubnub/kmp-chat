package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.v2.callbacks.Result

interface Chat {
    val pubNub: PubNub

    fun createUser(
        id: String,
        name: String? = null,
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
        // TODO change nulls to Optionals when there is support
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
        callback: (Result<User>) -> Unit
    )

    fun deleteUser(id: String, soft: Boolean = false, callback: (Result<User>) -> Unit)

    fun wherePresent(userId: String, callback: (Result<List<String>>) -> Unit)

    fun isPresent(userId: String, channel: String, callback: (Result<Boolean>) -> Unit)

    fun updateChannel(
        id: String,
        // TODO change nulls to Optionals when there is support
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        updated: String? = null,
        status: String? = null,
        type: ChannelType? = null,
        callback: (Result<Channel>) -> Unit
    )

    fun deleteChannel(id: String, soft: Boolean, callback: (Result<Channel>) -> Unit)

    fun forwardMessage(message: Message, id: String, callback: (Result<Unit>) -> Unit)

}
