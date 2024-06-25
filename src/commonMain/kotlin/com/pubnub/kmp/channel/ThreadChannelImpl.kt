package com.pubnub.kmp.channel

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.kmp.Chat
import com.pubnub.kmp.DELETED
import com.pubnub.kmp.Message
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.ThreadChannel
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.thenAsync
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import kotlinx.datetime.Clock

data class ThreadChannelImpl(
    override val parentMessage: Message,
    private val chat: Chat,
    override val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
    private var threadCreated: Boolean = true,
) : BaseChannel<ThreadChannel>(
    chat,
    clock,
    id,
    name,
    custom,
    description,
    updated,
    status,
    type,
    { chat, pnChannelMetadata ->
        fromDTO(chat, parentMessage, pnChannelMetadata)
    }
), ThreadChannel {

    override val parentChannelId: String
        get() = parentMessage.channelId

    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean?,
        usePost: Boolean,
        ttl: Int?,
        mentionedUsers: MessageMentionedUsers?,
        referencedChannels: Map<Int, MessageReferencedChannel>?,
        textLinks: List<TextLink>?,
        quotedMessage: Message?,
        files: List<File>?,
    ): PNFuture<PNPublishResult> {
        return (if (!threadCreated) {
            awaitAll(
                chat.pubNub.setChannelMetadata(id, description = description),
                chat.pubNub.addMessageAction(
                    parentMessage.channelId, PNMessageAction(
                        "threadRootId", id, parentMessage.timetoken
                    )
                )
            )
        } else {
            Unit.asFuture()
        }).thenAsync {
            threadCreated = true
            super.sendText(
                text,
                meta,
                shouldStore,
                usePost,
                ttl,
                mentionedUsers,
                referencedChannels,
                textLinks,
                quotedMessage,
                files
            )
        }
    }

    override fun copyWithStatusDeleted(): ThreadChannel = copy(status = DELETED)

    companion object {
        internal fun fromDTO(chat: Chat, parentMessage: Message, channel: PNChannelMetadata): ThreadChannel {
            return ThreadChannelImpl(
                parentMessage,
                chat,
                id = channel.id,
                name = channel.name,
                custom = channel.custom,
                description = channel.description,
                updated = channel.updated,
                status = channel.status,
                type = ChannelType.from(channel.type)
            )
        }
    }
}