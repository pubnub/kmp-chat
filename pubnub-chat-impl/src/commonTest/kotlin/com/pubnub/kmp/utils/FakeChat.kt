package com.pubnub.kmp.utils

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.chat.Channel
import com.pubnub.chat.ChannelGroup
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.timer.TimerManager
import com.pubnub.chat.internal.timer.createTimerManager
import com.pubnub.chat.listeners.ConnectionStatus
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.message.MarkAllMessageAsReadResponse
import com.pubnub.chat.message.UnreadMessagesCounts
import com.pubnub.chat.mutelist.MutedUsersManager
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
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
import kotlin.time.Duration

abstract class FakeChat(override val config: ChatConfiguration, override val pubNub: PubNub) : ChatInternal {
    override val timerManager: TimerManager = createTimerManager()

    override val mutedUsersManager: MutedUsersManager
        get() = TODO("Not yet implemented")

    override fun destroy() {
        TODO("Not yet implemented")
    }

    override val reactionsActionName: String
        get() = TODO("Not yet implemented")

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

    override fun getMessageFromReport(
        reportEvent: Event<EventContent.Report>,
        lookupBefore: Duration,
        lookupAfter: Duration
    ): PNFuture<Message?> {
        TODO("Not yet implemented")
    }

    override fun removeThreadChannel(
        chat: Chat,
        message: Message,
        soft: Boolean
    ): PNFuture<Pair<PNRemoveMessageActionResult, Channel?>> {
        TODO("Not yet implemented")
    }

    override fun getCurrentUserMentions(
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int
    ): PNFuture<GetCurrentUserMentionsResult> {
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

    override fun deleteUser(id: String, soft: Boolean): PNFuture<User?> {
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
        status: String?,
        type: ChannelType?,
    ): PNFuture<Channel> {
        TODO("Not yet implemented")
    }

    override fun deleteChannel(id: String, soft: Boolean): PNFuture<Channel?> {
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

    override fun createPublicConversation(
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?
    ): PNFuture<Channel> {
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
        membershipCustom: CustomObject?,
    ): PNFuture<CreateGroupConversationResult> {
        TODO("Not yet implemented")
    }

    override fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channelId: String,
        customMethod: EmitEventMethod,
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

    override fun fetchUnreadMessagesCounts(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>
    ): PNFuture<UnreadMessagesCounts> {
        TODO("Not yet implemented")
    }

    override fun getUnreadMessagesCounts(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<List<GetUnreadMessagesCounts>> {
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

    override fun getChannelSuggestions(text: String, limit: Int): PNFuture<List<Channel>> {
        TODO("Not yet implemented")
    }

    override fun getUserSuggestions(text: String, limit: Int): PNFuture<List<User>> {
        TODO("Not yet implemented")
    }

    override fun getPushChannels(): PNFuture<List<String>> {
        TODO("Not yet implemented")
    }

    override fun unregisterAllPushChannels(): PNFuture<Unit> {
        TODO("Not yet implemented")
    }

    override fun restoreThreadChannel(message: Message): PNFuture<PNMessageAction?> {
        TODO("Not yet implemented")
    }

    override fun getChannelGroup(id: String): ChannelGroup {
        TODO("Not yet implemented")
    }

    override fun removeChannelGroup(id: String): PNFuture<Unit> {
        TODO("Not yet implemented")
    }

    override fun addConnectionStatusListener(callback: (ConnectionStatus) -> Unit): AutoCloseable {
        TODO("Not yet implemented")
    }

    override fun reconnectSubscriptions(): PNFuture<Unit> {
        TODO("Not yet implemented")
    }

    override fun disconnectSubscriptions(): PNFuture<Unit> {
        TODO("Not yet implemented")
    }
}
