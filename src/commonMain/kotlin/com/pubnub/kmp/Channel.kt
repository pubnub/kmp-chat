package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.files.PNDeleteFileResult
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteChannelMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetChannelMetadataEventMessage
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.kmp.channel.BaseChannel
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.membership.MembersResponse
import com.pubnub.kmp.restrictions.GetRestrictionsResponse
import com.pubnub.kmp.restrictions.Restriction
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.GetFilesResult
import com.pubnub.kmp.types.InputFile
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
    fun join(custom: CustomObject? = null, callback: (Message) -> Unit): PNFuture<JoinResult>
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

    fun streamUpdates(callback: (channel: Channel) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) {
            callback(it.first())
        }
    }

    fun streamReadReceipts(callback: (receipts: Map<String, List<String>>) -> Unit): AutoCloseable

    fun getFiles(limit: Int = 100, next: String? = null): PNFuture<GetFilesResult>

    fun deleteFile(id: String, name: String): PNFuture<PNDeleteFileResult>

    fun streamPresence(callback: (userIds: Collection<String>) -> Unit): AutoCloseable

    fun getUserSuggestions(text: String, limit: Int = 10): PNFuture<Set<Membership>>

    companion object {
        fun streamUpdatesOn(
            channels: Collection<Channel>,
            callback: (channels: Collection<Channel>) -> Unit
        ): AutoCloseable {
            if (channels.isEmpty()) {
                throw PubNubException("Cannot stream channel updates on an empty list")
            }
            val chat = (channels.first() as BaseChannel<*, *>).chat
            val listener = createEventListener(chat.pubNub, onObjects = { pubNub, event ->
                val newChannel = when (val message = event.extractedMessage) {
                    is PNSetChannelMetadataEventMessage -> ChannelImpl.fromDTO(chat, message.data)
                    is PNDeleteChannelMetadataEventMessage -> ChannelImpl(
                        chat,
                        id = event.channel
                    ) // todo verify behavior with TS Chat SDK
                    else -> return@createEventListener
                }
                val newChannels = channels.map {
                    if (it.id == newChannel.id) {
                        newChannel
                    } else {
                        it
                    }
                }
                callback(newChannels)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(channels.map { it.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }
    }
}