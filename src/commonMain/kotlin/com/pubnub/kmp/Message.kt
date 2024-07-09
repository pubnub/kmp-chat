package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.kmp.channel.BaseChannel
import com.pubnub.kmp.message.BaseMessage
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannels
import com.pubnub.kmp.types.QuotedMessage
import com.pubnub.kmp.types.TextLink

interface Message {
    val timetoken: Long
    val content: EventContent.TextMessageContent
    val channelId: String
    val userId: String
    val actions: Map<String, Map<String, List<Action>>>?
    val meta: Map<String, Any>?
    val mentionedUsers: MessageMentionedUsers?
    val referencedChannels: MessageReferencedChannels?
    val quotedMessage: QuotedMessage?
    val text: String
    val deleted: Boolean
    val hasThread: Boolean
    val type: String
    val files: List<File>
    val reactions: Map<String, List<Action>>
    val textLinks: List<TextLink>?

    fun hasUserReaction(reaction: String): Boolean

    fun editText(newText: String): PNFuture<Message>

    fun delete(soft: Boolean = false, preserveFiles: Boolean = false): PNFuture<Message?>

    fun getThread(): PNFuture<ThreadChannel>

    fun forward(channelId: String): PNFuture<PNPublishResult>

    fun pin(): PNFuture<Channel>

    fun report(reason: String): PNFuture<PNPublishResult>

    fun createThread(): PNFuture<ThreadChannel>

    fun removeThread(): PNFuture<Pair<PNRemoveMessageActionResult, Channel>>

    fun toggleReaction(reaction: String): PNFuture<Message>

    fun streamUpdates(callback: (message: Message) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) {
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
            val chat = (messages.first() as BaseChannel<*, *>).chat
            val listener = createEventListener(chat.pubNub, onMessageAction = { pubNub, event ->
                val message =
                    messages.find { it.timetoken == event.messageAction.messageTimetoken } ?: return@createEventListener
                if (message.channelId != event.channel) return@createEventListener
                val actions = if (event.event == "added") {
                    BaseMessage.assignAction(message.actions, event.messageAction)
                } else {
                    BaseMessage.filterAction(message.actions, event.messageAction)
                }
                val newMessage = (message as BaseMessage<*>).copyWithActions(actions)
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
    }
}
