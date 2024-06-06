package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.channel.GetChannelsResponse
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.user.GetUsersResponse

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

    fun getUser(userId: String, callback: (Result<User>) -> Unit)


    fun getUsers(
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        limit: Int? = null,
        page: PNPage? = null,
        callback: (Result<GetUsersResponse>) -> Unit
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

    fun getChannels(
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        limit: Int? = null,
        page: PNPage? = null,
        callback: (Result<GetChannelsResponse>) -> Unit
    )

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

    fun <T : EventContent> emitEvent(
        channel: String,
        method: EmitEventMethod = EmitEventMethod.SIGNAL,
        type: String = "custom",
        payload: T,
        callback: (Result<PNPublishResult>) -> Unit
    )
}
