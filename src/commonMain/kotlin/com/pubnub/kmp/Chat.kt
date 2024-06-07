package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.types.EventContent

interface Chat {
    val config: ChatConfig
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

    fun createChannel(
        id: String,
        name: String? = null,
        description: String? = null,
        custom: CustomObject? = null,
        type: ChannelType? = null,
        status: String? = null,
        callback: (Result<Channel>) -> Unit
    )

    fun getChannel(channelId: String, callback: (Result<Channel>) -> Unit)

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

    fun forwardMessage(message: Message, channelId: String, callback: (Result<Unit>) -> Unit)

    fun whoIsPresent(channelId: String, callback: (Result<Collection<String>>) -> Unit)

    fun publish(channelId: String,
                message: EventContent,
                meta: Map<String,Any>? = null,
                shouldStore: Boolean? = null,
                usePost: Boolean = false,
                replicate: Boolean = true,
                ttl: Int? = null,
                callback: (Result<PNPublishResult>) -> Unit,
    )

    fun <T : EventContent> emitEvent(
        channelOrUser: String,
        payload: T,
        callback: (Result<PNPublishResult>) -> Unit
    )
}
