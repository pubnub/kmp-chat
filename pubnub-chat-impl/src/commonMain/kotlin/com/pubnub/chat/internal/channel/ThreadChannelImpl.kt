package com.pubnub.chat.internal.channel

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatImpl.Companion
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.internal.DELETED
import com.pubnub.chat.internal.message.ThreadMessageImpl
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import kotlinx.datetime.Clock

data class ThreadChannelImpl(
    override val parentMessage: com.pubnub.chat.Message,
    override val chat: Chat,
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
                error("Parent channel doesn't exist")
            }
            ChatImpl.pinMessageToChannel(chat.pubNub, message, parentChannel).then {
                ChannelImpl.fromDTO(chat, it.data!!)
            }
        }
    }

    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        mentionedUsers: com.pubnub.chat.types.MessageMentionedUsers?,
        referencedChannels: Map<Int, MessageReferencedChannel>?,
        textLinks: List<com.pubnub.chat.types.TextLink>?,
        quotedMessage: com.pubnub.chat.Message?,
        files: List<InputFile>?,
    ): PNFuture<PNPublishResult> {
        return (
            if (!threadCreated) {
                awaitAll(
                    chat.pubNub.setChannelMetadata(id, description = description),
                    chat.pubNub.addMessageAction(
                        parentMessage.channelId,
                        PNMessageAction(
                            "threadRootId",
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

    override fun streamUpdates(callback: (channel: Channel) -> Unit): AutoCloseable {
        TODO()
    }

    override fun copyWithStatusDeleted(): ThreadChannel = copy(status = DELETED)

    companion object {
        internal fun fromDTO(chat: Chat, parentMessage: com.pubnub.chat.Message, channel: PNChannelMetadata): ThreadChannel {
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
