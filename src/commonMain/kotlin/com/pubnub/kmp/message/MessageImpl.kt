package com.pubnub.kmp.message

import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.Chat
import com.pubnub.kmp.Message
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannels
import com.pubnub.kmp.types.QuotedMessage

data class MessageImpl(
    private val chat: Chat,
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
    override fun copyWithActions(actions: Actions): Message = copy(actions = actions)

    companion object {
        internal fun fromDTO(chat: Chat, pnMessageResult: PNMessageResult): Message {
            return MessageImpl(
                chat,
                pnMessageResult.timetoken!!,
                PNDataEncoder.decode<EventContent>(pnMessageResult.message) as EventContent.TextMessageContent,
                pnMessageResult.channel,
                pnMessageResult.publisher!!,
                meta = pnMessageResult.userMetadata?.decode() as? Map<String, Any>,
                mentionedUsers = pnMessageResult.userMetadata.extractMentionedUsers(),
                referencedChannels = pnMessageResult.userMetadata.extractReferencedChannels(),
                quotedMessage = pnMessageResult.userMetadata.extractQuotedMessage()
            )
        }

        internal fun fromDTO(chat: Chat, messageItem: PNFetchMessageItem, channelId: String): Message {
            val eventContent = try {
                messageItem.message.asString()?.let { text ->
                    EventContent.TextMessageContent(text, null)
                } ?: PNDataEncoder.decode(messageItem.message)
            } catch (e: Exception) {
                EventContent.UnknownMessageFormat(messageItem.message)
            }

            return MessageImpl(
                chat,
                messageItem.timetoken!!,
                eventContent,
                channelId,
                messageItem.uuid!!,
                messageItem.actions,
                messageItem.meta?.decode()?.let { it as Map<String, Any>? },
                mentionedUsers = messageItem.meta.extractMentionedUsers(),
                referencedChannels = messageItem.meta.extractReferencedChannels(),
                quotedMessage = messageItem.meta.extractQuotedMessage()
            )
        }
    }
}
