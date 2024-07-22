package com.pubnub.kmp.utils

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.chat.Channel
import com.pubnub.chat.Event
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.message.MarkAllMessageAsReadResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.PubNub
import kotlin.reflect.KClass

abstract class FakeChat(override val config: ChatConfiguration, override val pubNub: PubNub) : ChatInternal {
    override val currentUser: User
        get() = TODO("Not yet implemented")

    override fun getEventsHistory(
        channelId: String,
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int
    ): PNFuture<GetEventsHistoryResult> {
        TODO("Not yet implemented")
    }

    override val editMessageActionName: String
        get() = TODO("Not yet implemented")
    override val deleteMessageActionName: String
        get() = TODO("Not yet implemented")

    override fun createUser(user: User): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun createUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?,
    ): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun getUser(userId: String): PNFuture<User?> {
        TODO("Not yet implemented")
    }

    override fun getUsers(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetUsersResponse> {
        TODO("Not yet implemented")
    }

    override fun updateUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?,
    ): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun deleteUser(id: String, soft: Boolean): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun wherePresent(userId: String): PNFuture<List<String>> {
        TODO("Not yet implemented")
    }

    override fun isPresent(userId: String, channelId: String): PNFuture<Boolean> {
        TODO("Not yet implemented")
    }

    override fun createChannel(
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?,
    ): PNFuture<Channel> {
        TODO("Not yet implemented")
    }

    override fun getChannel(channelId: String): PNFuture<Channel?> {
        TODO("Not yet implemented")
    }

    override fun getChannels(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetChannelsResponse> {
        TODO("Not yet implemented")
    }

    override fun updateChannel(
        id: String,
        name: String?,
        custom: CustomObject?,
        description: String?,
        updated: String?,
        status: String?,
        type: ChannelType?,
    ): PNFuture<Channel> {
        TODO("Not yet implemented")
    }

    override fun deleteChannel(id: String, soft: Boolean): PNFuture<Channel> {
        TODO("Not yet implemented")
    }

    override fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun whoIsPresent(channelId: String): PNFuture<Collection<String>> {
        TODO("Not yet implemented")
    }

    override fun <T : EventContent> emitEvent(
        channelId: String,
        payload: T,
        mergePayloadWith: Map<String, Any>?,
    ): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun createDirectConversation(
        invitedUser: User,
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?,
        membershipCustom: CustomObject?,
    ): PNFuture<com.pubnub.chat.types.CreateDirectConversationResult> {
        TODO("Not yet implemented")
    }

    override fun createGroupConversation(
        invitedUsers: Collection<User>,
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?,
        custom: CustomObject?,
    ): PNFuture<CreateGroupConversationResult> {
        TODO("Not yet implemented")
    }

    override fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channelId: String,
        customMethod: EmitEventMethod?,
        callback: (event: Event<T>) -> Unit,
    ): AutoCloseable {
        TODO("Not yet implemented")
    }

    override fun setRestrictions(restriction: Restriction): PNFuture<Unit> {
        TODO("Not yet implemented")
    }

    override fun registerPushChannels(channels: List<String>): PNFuture<PNPushAddChannelResult> {
        TODO("Not yet implemented")
    }

    override fun unregisterPushChannels(channels: List<String>): PNFuture<PNPushRemoveChannelResult> {
        TODO("Not yet implemented")
    }

    override fun getThreadChannel(message: Message): PNFuture<ThreadChannel> {
        TODO("Not yet implemented")
    }

    override fun getUnreadMessagesCounts(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<Set<GetUnreadMessagesCounts>> {
        TODO("Not yet implemented")
    }

    override fun markAllMessagesAsRead(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<MarkAllMessageAsReadResponse> {
        TODO("Not yet implemented")
    }

    override fun getChannelSuggestions(text: String, limit: Int): PNFuture<Set<Channel>> {
        TODO("Not yet implemented")
    }

    override fun publish(
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?,
        mergeMessageWith: Map<String, Any>?,
    ): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun signal(
        channelId: String,
        message: EventContent,
        mergeMessageWith: Map<String, Any>?,
    ): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun getUserSuggestions(text: String, limit: Int): PNFuture<Set<User>> {
        TODO("Not yet implemented")
    }

    override fun getPushChannels(): PNFuture<List<String>> {
        TODO("Not yet implemented")
    }

    override fun unregisterAllPushChannels(): PNFuture<Unit> {
        TODO("Not yet implemented")
    }
}
