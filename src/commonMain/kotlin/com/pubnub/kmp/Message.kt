package com.pubnub.kmp

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
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
}
