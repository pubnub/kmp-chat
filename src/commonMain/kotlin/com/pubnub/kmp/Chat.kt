package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.kmp.channel.GetChannelsResponse
import com.pubnub.kmp.types.CreateDirectConversationResult
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.user.GetUsersResponse

interface Chat {
    val config: ChatConfig
    val pubNub: PubNub
//
//    suspend fun createUser(
//        id: String,
//        name: String? = null,
//        externalId: String? = null,
//        profileUrl: String? = null,
//        email: String? = null,
//        custom: CustomObject? = null,
//        status: String? = null,
//        type: String? = null
//    ): User

    fun createUser(
        id: String,
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
    ): PNFuture<User>

    fun getUser(userId: String): PNFuture<User?>


    fun getUsers(
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        limit: Int? = null,
        page: PNPage? = null,
    ): PNFuture<GetUsersResponse>


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
    ): PNFuture<User>

    fun deleteUser(id: String, soft: Boolean = false): PNFuture<User>

    fun wherePresent(userId: String): PNFuture<List<String>>

    fun isPresent(userId: String, channel: String): PNFuture<Boolean>

    fun createChannel(
        id: String,
        name: String? = null,
        description: String? = null,
        custom: CustomObject? = null,
        type: ChannelType? = null,
        status: String? = null,
    ): PNFuture<Channel>

    fun getChannel(channelId: String): PNFuture<Channel?>

    fun getChannels(
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        limit: Int? = null,
        page: PNPage? = null,
    ): PNFuture<GetChannelsResponse>

    fun updateChannel(
        id: String,
        // TODO change nulls to Optionals when there is support
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        updated: String? = null,
        status: String? = null,
        type: ChannelType? = null,
    ): PNFuture<Channel>

    fun deleteChannel(id: String, soft: Boolean): PNFuture<Channel>

    fun forwardMessage(message: Message, channelId: String): PNFuture<Unit>

    fun whoIsPresent(channelId: String): PNFuture<Collection<String>>

    fun publish(
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean? = null,
        usePost: Boolean = false,
        replicate: Boolean = true,
        ttl: Int? = null,
    ): PNFuture<PNPublishResult>

    fun <T : EventContent> emitEvent(
        channel: String,
        payload: T,
    ): PNFuture<PNPublishResult>

    fun createDirectConversation(
        invitedUser: User,
        channelId: String? = null,
        channelData: Any? = null,
        membershipData: Any? = null,
    ): PNFuture<CreateDirectConversationResult>
    
    fun signal(channelId: String, message: EventContent): PNFuture<PNPublishResult>
}
