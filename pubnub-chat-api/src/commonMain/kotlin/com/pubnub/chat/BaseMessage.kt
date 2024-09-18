package com.pubnub.chat

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.PNFuture

// todo do we have tests for methods in this class?
interface BaseMessage<T : BaseMessage<T>> {
    val chat: Chat
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

    fun editText(newText: String): PNFuture<T>

    fun delete(soft: Boolean = false, preserveFiles: Boolean = false): PNFuture<T?>

    fun getThread(): PNFuture<ThreadChannel>

    fun forward(channelId: String): PNFuture<PNPublishResult>

    fun pin(): PNFuture<Channel>

    // todo do we have test for this?
    fun report(reason: String): PNFuture<PNPublishResult>

    fun createThread(): PNFuture<ThreadChannel>

    // todo do we have test for this?
    fun removeThread(): PNFuture<Pair<PNRemoveMessageActionResult, Channel>>

    fun toggleReaction(reaction: String): PNFuture<T>

    // todo do we want to have test for this?
    fun streamUpdates(callback: (message: T) -> Unit): AutoCloseable

    fun restore(): PNFuture<T>

    companion object
}
