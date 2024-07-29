package com.pubnub.chat

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.message.MarkAllMessageAsReadResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateDirectConversationResult
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.types.GetCurrentUserMentionsResult
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import kotlin.reflect.KClass

interface Chat {
    val config: ChatConfiguration
    val pubNub: PubNub // todo change to `sdk` like in TS
    val currentUser: User

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

    fun isPresent(userId: String, channelId: String): PNFuture<Boolean>

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
        status: String? = null,
        type: ChannelType? = null,
    ): PNFuture<Channel>

    fun deleteChannel(id: String, soft: Boolean = false): PNFuture<Channel>

    fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult>

    fun whoIsPresent(channelId: String): PNFuture<Collection<String>>

    fun <T : EventContent> emitEvent(
        channelId: String,
        payload: T,
        mergePayloadWith: Map<String, Any>? = null,
    ): PNFuture<PNPublishResult>

    fun createPublicConversation(
        channelId: String? = null,
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
    ): PNFuture<Channel>

    fun createDirectConversation(
        invitedUser: User,
        channelId: String? = null,
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
        membershipCustom: CustomObject? = null,
    ): PNFuture<CreateDirectConversationResult>

    fun createGroupConversation(
        invitedUsers: Collection<User>,
        channelId: String? = null,
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
        membershipCustom: CustomObject? = null,
    ): PNFuture<CreateGroupConversationResult>

    fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channelId: String,
        customMethod: EmitEventMethod? = null,
        callback: (event: Event<T>) -> Unit
    ): AutoCloseable

    fun setRestrictions(
        restriction: Restriction
    ): PNFuture<Unit>

    fun registerPushChannels(channels: List<String>): PNFuture<PNPushAddChannelResult>

    fun unregisterPushChannels(channels: List<String>): PNFuture<PNPushRemoveChannelResult>

    fun unregisterAllPushChannels(): PNFuture<Unit>

    fun getThreadChannel(message: Message): PNFuture<ThreadChannel>

    fun getUnreadMessagesCounts(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<Set<GetUnreadMessagesCounts>>

    fun markAllMessagesAsRead(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<MarkAllMessageAsReadResponse>

    fun getChannelSuggestions(text: String, limit: Int = 10): PNFuture<Set<Channel>>

    fun getUserSuggestions(text: String, limit: Int = 10): PNFuture<Set<User>>

    fun getPushChannels(): PNFuture<List<String>>

    fun getEventsHistory(
        channelId: String,
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 100
    ): PNFuture<GetEventsHistoryResult>

    fun getCurrentUserMentions(
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 100
    ): PNFuture<GetCurrentUserMentionsResult>

    // Companion object required for extending this class elsewhere
    companion object
}

inline fun <reified T : EventContent> Chat.listenForEvents(
    channelId: String,
    customMethod: EmitEventMethod? = null,
    noinline callback: (event: Event<T>) -> Unit
): AutoCloseable {
    return listenForEvents(T::class, channelId, customMethod, callback)
}
