package com.pubnub.chat.internal.channel

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.DELETED
import com.pubnub.chat.internal.THREAD_ROOT_ID
import com.pubnub.chat.internal.error.PubNubErrorMessage.PARENT_CHANNEL_DOES_NOT_EXISTS
import com.pubnub.chat.internal.message.ThreadMessageImpl
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import kotlinx.datetime.Clock
import org.lighthousegames.logging.logging

data class ThreadChannelImpl(
    override val parentMessage: Message,
    override val chat: ChatInternal,
    val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
    private var threadCreated: Boolean = true,
) : BaseChannel<ThreadChannel, ThreadMessage>(
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
        },
        { chat, pnMessageItem, channelId ->
            ThreadMessageImpl.fromDTO(chat, pnMessageItem, channelId, parentMessage.channelId)
        }
    ),
    ThreadChannel {
    override val parentChannelId: String
        get() = parentMessage.channelId

    override fun pinMessageToParentChannel(message: ThreadMessage) = pinOrUnpinMessageFromParentChannel(message)

    override fun unpinMessageFromParentChannel(): PNFuture<Channel> = pinOrUnpinMessageFromParentChannel(null)

    private fun pinOrUnpinMessageFromParentChannel(message: ThreadMessage?): PNFuture<Channel> {
        return chat.getChannel(parentChannelId).thenAsync { parentChannel ->
            if (parentChannel == null) {
                log.pnError(PARENT_CHANNEL_DOES_NOT_EXISTS)
            }
            ChatImpl.pinMessageToChannel(chat.pubNub, message, parentChannel).then {
                ChannelImpl.fromDTO(chat, it.data)
            }
        }
    }

    override fun delete(soft: Boolean): PNFuture<Channel> {
        return chat.removeThreadChannel(chat, parentMessage, soft).then { it.second }
    }

    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        mentionedUsers: MessageMentionedUsers?,
        referencedChannels: MessageReferencedChannels?,
        textLinks: List<com.pubnub.chat.types.TextLink>?,
        quotedMessage: Message?,
        files: List<InputFile>?,
    ): PNFuture<PNPublishResult> {
        return (
            if (!threadCreated) {
                awaitAll(
                    chat.pubNub.setChannelMetadata(id, description = description),
                    chat.pubNub.addMessageAction(
                        parentMessage.channelId,
                        PNMessageAction(
                            THREAD_ROOT_ID,
                            id,
                            parentMessage.timetoken
                        )
                    )
                )
            } else {
                Unit.asFuture()
            }
        ).thenAsync {
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

    override fun emitUserMention(userId: String, timetoken: Long, text: String): PNFuture<PNPublishResult> {
        return chat.emitEvent(
            userId,
            EventContent.Mention(timetoken, id, parentChannelId),
            getPushPayload(this, text, chat.config.pushNotifications)
        )
    }

    companion object {
        private val log = logging()

        internal fun fromDTO(chat: ChatInternal, parentMessage: Message, channel: PNChannelMetadata): ThreadChannel {
            return ThreadChannelImpl(
                parentMessage,
                chat,
                id = channel.id,
                name = channel.name?.value,
                custom = channel.custom?.value,
                description = channel.description?.value,
                updated = channel.updated?.value,
                status = channel.status?.value,
                type = ChannelType.from(channel.type?.value)
            )
        }
    }
}
