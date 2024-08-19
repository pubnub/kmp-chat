package com.pubnub.chat.internal.message

import com.pubnub.api.decode
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.chat.Message
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.defaultGetMessageResponseBody
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.QuotedMessage

data class MessageImpl(
    override val chat: ChatInternal,
    override val timetoken: Long,
    override val content: EventContent.TextMessageContent,
    override val channelId: String,
    override val userId: String,
    override val actions: Map<String, Map<String, List<Action>>>? = null,
    override val meta: Map<String, Any>? = null,
    override val mentionedUsers: MessageMentionedUsers? = null,
    override val referencedChannels: MessageReferencedChannels? = null,
    override val quotedMessage: QuotedMessage? = null,
) : BaseMessage<Message>(
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
    ) {
    override fun copyWithActions(actions: Actions?): Message = copy(actions = actions)

    companion object {
        internal fun fromDTO(chat: ChatInternal, pnMessageResult: PNMessageResult): Message {
            val content = chat.config.customPayloads?.getMessageResponseBody?.invoke(pnMessageResult.message) ?: defaultGetMessageResponseBody(pnMessageResult.message)
            return MessageImpl(
                chat,
                pnMessageResult.timetoken!!,
                content!!, // todo handle malformed content (then this is null)
                pnMessageResult.channel,
                pnMessageResult.publisher!!,
                meta = pnMessageResult.userMetadata?.decode() as? Map<String, Any>,
                mentionedUsers = pnMessageResult.userMetadata.extractMentionedUsers(),
                referencedChannels = pnMessageResult.userMetadata.extractReferencedChannels(),
                quotedMessage = pnMessageResult.userMetadata.extractQuotedMessage()
            )
        }

        internal fun fromDTO(chat: ChatInternal, messageItem: PNFetchMessageItem, channelId: String): Message {
            val content = chat.config.customPayloads?.getMessageResponseBody?.invoke(messageItem.message) ?: defaultGetMessageResponseBody(messageItem.message)

            return MessageImpl(
                chat,
                messageItem.timetoken!!,
                content!!,
                channelId,
                messageItem.uuid!!,
                messageItem.actions,
                messageItem.meta?.decode()?.let { it as? Map<String, Any>? },
                mentionedUsers = messageItem.meta.extractMentionedUsers(),
                referencedChannels = messageItem.meta.extractReferencedChannels(),
                quotedMessage = messageItem.meta.extractQuotedMessage()
            )
        }
    }
}
