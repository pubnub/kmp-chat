package com.pubnub.chat.internal.message

import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.chat.Channel
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage.PARENT_CHANNEL_DOES_NOT_EXISTS
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import org.lighthousegames.logging.logging

data class ThreadMessageImpl(
    override val chat: ChatInternal,
    override val parentChannelId: String,
    override val timetoken: Long,
    override val content: EventContent.TextMessageContent,
    override val channelId: String,
    override val userId: String,
    override val actions: Map<String, Map<String, List<Action>>>? = null,
    override val meta: Map<String, Any>? = null,
    override val mentionedUsers: MessageMentionedUsers? = null,
    override val referencedChannels: MessageReferencedChannels? = null,
    override val quotedMessage: QuotedMessage? = null,
) : BaseMessage<ThreadMessage>(
        chat = chat,
        timetoken = timetoken,
        content = content,
        channelId = channelId,
        userId = userId,
        actions = actions,
        meta = meta,
        mentionedUsers = mentionedUsers,
        referencedChannels = referencedChannels,
        quotedMessage = quotedMessage
    ),
    ThreadMessage {
    override fun copyWithActions(actions: Actions?): ThreadMessage = copy(actions = actions)

    companion object {
        private val log = logging()

        internal fun fromDTO(chat: ChatImpl, pnMessageResult: PNMessageResult, parentChannelId: String): ThreadMessage {
            return ThreadMessageImpl(
                chat,
                parentChannelId,
                pnMessageResult.timetoken!!,
                PNDataEncoder.decode<EventContent>(pnMessageResult.message) as EventContent.TextMessageContent,
                pnMessageResult.channel,
                pnMessageResult.publisher!!,
                meta = pnMessageResult.userMetadata?.decode() as? Map<String, Any>,
                mentionedUsers = pnMessageResult.userMetadata.extractMentionedUsers(),
                referencedChannels = pnMessageResult.userMetadata.extractReferencedChannels(),
                quotedMessage = pnMessageResult.userMetadata?.let { PNDataEncoder.decode(it) },
            )
        }

        internal fun fromDTO(chat: ChatInternal, messageItem: PNFetchMessageItem, channelId: String, parentChannelId: String): ThreadMessage {
            val eventContent = try {
                messageItem.message.asString()?.let { text ->
                    EventContent.TextMessageContent(text, null)
                } ?: PNDataEncoder.decode(messageItem.message)
            } catch (e: Exception) {
                EventContent.UnknownMessageFormat(messageItem.message)
            }

            return ThreadMessageImpl(
                chat = chat,
                parentChannelId = parentChannelId,
                timetoken = messageItem.timetoken!!,
                content = eventContent,
                channelId = channelId,
                userId = messageItem.uuid!!,
                actions = messageItem.actions,
                meta = messageItem.meta?.decode()?.let { it as Map<String, Any>? },
                mentionedUsers = messageItem.meta.extractMentionedUsers(),
                referencedChannels = messageItem.meta.extractReferencedChannels(),
                quotedMessage = messageItem.meta?.let { PNDataEncoder.decode(it) },
            )
        }
    }

    override fun pinToParentChannel() = pinOrUnpinFromParentChannel(this)

    override fun unpinFromParentChannel() = pinOrUnpinFromParentChannel(null)

    private fun pinOrUnpinFromParentChannel(message: ThreadMessage?): PNFuture<Channel> {
        return chat.getChannel(parentChannelId).thenAsync { parentChannel ->
            if (parentChannel == null) {
                log.pnError(PARENT_CHANNEL_DOES_NOT_EXISTS)
            }
            ChatImpl.pinMessageToChannel(chat.pubNub, message, parentChannel).then {
                ChannelImpl.fromDTO(chat, it.data)
            }
        }
    }
}
