package com.pubnub.kmp

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.kmp.membership.MembersResponse
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.JoinResult
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink

interface Channel {
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
        updated: String? = null,
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
        // todo add paging in response
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int? = 25,
    ): PNFuture<List<Message>>

    fun sendText(
        text: String,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean? = null,
        usePost: Boolean = false,
        ttl: Int? = null,
        mentionedUsers: MessageMentionedUsers? = null,
        referencedChannels: Map<Int, MessageReferencedChannel>? = null,
        textLinks: List<TextLink>? = null,
        quotedMessage: Message? = null,
        files: List<File>? = null,
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
    fun join(custom: CustomObject? = null, callback: (Message) -> Unit): PNFuture<JoinResult>
    fun leave(): PNFuture<Unit>
    fun getPinnedMessage(): PNFuture<Message?>
    fun getMessage(timetoken: Long): PNFuture<Message?>
    fun registerForPush(): PNFuture<PNPushAddChannelResult>
    fun unregisterFromPush(): PNFuture<PNPushRemoveChannelResult>
    fun pinMessage(message: Message): PNFuture<Channel>
    fun unpinMessage(): PNFuture<Channel>
}