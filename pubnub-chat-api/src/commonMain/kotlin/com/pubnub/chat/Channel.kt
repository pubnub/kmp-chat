package com.pubnub.chat

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.files.PNDeleteFileResult
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.types.GetFilesResult
import com.pubnub.chat.types.HistoryResponse
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.JoinResult
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

interface Channel {
    val chat: Chat
    val id: String
    val name: String?
    val custom: Map<String, Any?>?
    val description: String?
    val updated: String?
    val status: String?
    val type: ChannelType?

    fun update(
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        status: String? = null,
        type: ChannelType? = null,
    ): PNFuture<Channel>

    fun delete(soft: Boolean = false): PNFuture<Channel>

    fun forwardMessage(message: Message): PNFuture<PNPublishResult>

    fun startTyping(): PNFuture<Unit>

    fun stopTyping(): PNFuture<Unit>

    fun getTyping(callback: (typingUserIds: Collection<String>) -> Unit): AutoCloseable

    fun whoIsPresent(): PNFuture<Collection<String>>

    fun isPresent(userId: String): PNFuture<Boolean>

    fun getHistory(
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 25,
    ): PNFuture<HistoryResponse<*>>

    fun sendText(
        text: String,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean = true,
        usePost: Boolean = false,
        ttl: Int? = null,
        mentionedUsers: MessageMentionedUsers? = null,
        referencedChannels: Map<Int, MessageReferencedChannel>? = null,
        textLinks: List<TextLink>? = null,
        quotedMessage: Message? = null,
        files: List<InputFile>? = null,
    ): PNFuture<PNPublishResult>

    fun invite(user: User): PNFuture<Membership>

    fun inviteMultiple(users: Collection<User>): PNFuture<List<Membership>>

    fun getMembers(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMemberKey>> = listOf(),
    ): PNFuture<MembersResponse>

    fun connect(callback: (Message) -> Unit): AutoCloseable

    fun join(custom: CustomObject? = null, callback: ((Message) -> Unit)? = null): PNFuture<JoinResult>

    fun leave(): PNFuture<Unit>

    fun getPinnedMessage(): PNFuture<Message?>

    fun getMessage(timetoken: Long): PNFuture<Message?>

    fun registerForPush(): PNFuture<PNPushAddChannelResult>

    fun unregisterFromPush(): PNFuture<PNPushRemoveChannelResult>

    fun pinMessage(message: Message): PNFuture<Channel>

    fun unpinMessage(): PNFuture<Channel>

    fun setRestrictions(
        user: User,
        ban: Boolean = false,
        mute: Boolean = false,
        reason: String? = null
    ): PNFuture<Unit>

    fun getUserRestrictions(user: User): PNFuture<Restriction>

    fun getUsersRestrictions(
        limit: Int? = null,
        page: PNPage? = null,
        sort: Collection<PNSortKey<PNMemberKey>> = listOf()
    ): PNFuture<GetRestrictionsResponse>

    fun streamUpdates(callback: (channel: Channel?) -> Unit): AutoCloseable

    fun streamReadReceipts(callback: (receipts: Map<Long, List<String>>) -> Unit): AutoCloseable

    fun getFiles(limit: Int = 100, next: String? = null): PNFuture<GetFilesResult>

    fun deleteFile(id: String, name: String): PNFuture<PNDeleteFileResult>

    fun streamPresence(callback: (userIds: Collection<String>) -> Unit): AutoCloseable

    fun getUserSuggestions(text: String, limit: Int = 10): PNFuture<Set<Membership>>

    fun getMessageReportsHistory(
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 25,
    ): PNFuture<GetEventsHistoryResult>

    fun streamMessageReports(callback: (event: EventContent.Report) -> Unit): AutoCloseable

    // Companion object required for extending this class elsewhere
    companion object
}
