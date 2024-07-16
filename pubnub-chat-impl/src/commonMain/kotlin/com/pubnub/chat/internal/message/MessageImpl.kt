package com.pubnub.chat.internal.message

import com.pubnub.api.PubNubException
import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.chat.Chat
import com.pubnub.chat.Message
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.kmp.createEventListener

data class MessageImpl(
    override val chat: Chat,
    override val timetoken: Long,
    override val content: EventContent.TextMessageContent,
    override val channelId: String,
    override val userId: String,
    override val actions: Map<String, Map<String, List<Action>>>? = null,
    override val meta: Map<String, Any>? = null,
    override val mentionedUsers: com.pubnub.chat.types.MessageMentionedUsers? = null,
    override val referencedChannels: MessageReferencedChannels? = null,
    override val quotedMessage: QuotedMessage? = null,
) : com.pubnub.chat.internal.message.BaseMessage<Message>(
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
    override fun copyWithActions(actions: com.pubnub.chat.internal.message.Actions): Message = copy(actions = actions)

    override fun streamUpdates(callback: (message: Message) -> Unit): AutoCloseable {
        return com.pubnub.chat.internal.message.MessageImpl.Companion.streamUpdatesOn(listOf(this)) {
            callback(it.first())
        }
    }

    companion object {
        fun streamUpdatesOn(
            messages: Collection<Message>,
            callback: (messages: Collection<Message>) -> Unit,
        ): AutoCloseable {
            if (messages.isEmpty()) {
                throw PubNubException("Cannot stream message updates on an empty list")
            }
            val chat = messages.first().chat
            val listener = createEventListener(chat.pubNub, onMessageAction = { pubNub, event ->
                val message =
                    messages.find { it.timetoken == event.messageAction.messageTimetoken } ?: return@createEventListener
                if (message.channelId != event.channel) return@createEventListener
                val actions = if (event.event == "added") {
                    com.pubnub.chat.internal.message.BaseMessage.Companion.assignAction(
                        message.actions,
                        event.messageAction
                    )
                } else {
                    com.pubnub.chat.internal.message.BaseMessage.Companion.filterAction(
                        message.actions,
                        event.messageAction
                    )
                }
                val newMessage = (message as com.pubnub.chat.internal.message.BaseMessage<*>).copyWithActions(actions)
                val newMessages = messages.map {
                    if (it.timetoken == newMessage.timetoken) {
                        newMessage
                    } else {
                        it
                    }
                }
                callback(newMessages)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(
                messages.map { it.channelId }.toSet()
            )
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        internal fun fromDTO(chat: Chat, pnMessageResult: PNMessageResult): Message {
            return com.pubnub.chat.internal.message.MessageImpl(
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

            return com.pubnub.chat.internal.message.MessageImpl(
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
