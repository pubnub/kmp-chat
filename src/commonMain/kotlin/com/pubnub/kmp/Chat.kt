package com.pubnub.kmp

import com.benasher44.uuid.uuid4
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.kmp.channel.GetChannelsResponse
import com.pubnub.kmp.message.GetUnreadMessagesCounts
import com.pubnub.kmp.message.MarkAllMessageAsReadResponse
import com.pubnub.kmp.restrictions.Restriction
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.CreateDirectConversationResult
import com.pubnub.kmp.types.CreateGroupConversationResult
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.user.GetUsersResponse
import kotlin.reflect.KClass

interface Chat {
    val config: ChatConfig
    val pubNub: PubNub
    val currentUser: User

    val editMessageActionName: String
    val deleteMessageActionName: String

    fun createUser(user: User): PNFuture<User>

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

    fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult>

    fun whoIsPresent(channelId: String): PNFuture<Collection<String>>

    fun <T : EventContent> emitEvent(
        channel: String,
        payload: T,
        mergePayloadWith: Map<String, Any>? = null,
    ): PNFuture<PNPublishResult>

    fun createDirectConversation(
        invitedUser: User,
        channelId: String? = null,
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
        custom: CustomObject? = null,
    ): PNFuture<CreateDirectConversationResult>

    fun createGroupConversation(
        invitedUsers: Collection<User>,
        channelId: String = uuid4().toString(),
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
        custom: CustomObject? = null,
    ): PNFuture<CreateGroupConversationResult>

    fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channel: String,
        customMethod: EmitEventMethod? = null,
        callback: (event: Event<T>) -> Unit
    ): AutoCloseable

    fun setRestrictions(
        restriction: Restriction
    ): PNFuture<Unit>

    fun registerPushChannels(channels: List<String>): PNFuture<PNPushAddChannelResult>
    fun unregisterPushChannels(channels: List<String>): PNFuture<PNPushRemoveChannelResult>
    fun getThreadChannel(message: Message): PNFuture<ThreadChannel>
    fun getUnreadMessagesCounts(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ) : PNFuture<Set<GetUnreadMessagesCounts>>

    fun markAllMessagesAsRead(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<MarkAllMessageAsReadResponse>

    fun getChannelSuggestions(text: String, limit: Int = 10): PNFuture<Set<Channel>>

    /* should be internal */
    fun publish( //todo maybe create separate interface Chat : ChatInternal so that publish and signal are not visible by user?
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean? = null,
        usePost: Boolean = false,
        replicate: Boolean = true,
        ttl: Int? = null,
        mergeMessageWith: Map<String, Any>? = null,
    ): PNFuture<PNPublishResult>

    /* should be internal */
    fun signal(
        channelId: String,
        message: EventContent,
        mergeMessageWith: Map<String, Any>? = null
    ): PNFuture<PNPublishResult>
}

inline fun <reified T : EventContent> Chat.listenForEvents(
    channel: String,
    customMethod: EmitEventMethod? = null,
    noinline callback: (event: Event<T>) -> Unit
): AutoCloseable {
    return listenForEvents(T::class, channel, customMethod, callback)
}