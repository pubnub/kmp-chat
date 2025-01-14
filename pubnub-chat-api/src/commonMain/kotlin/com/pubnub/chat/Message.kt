package com.pubnub.chat

import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.kmp.PNFuture

/**
 * Represents an object that refers to a single message in a chat.
 */
interface Message : BaseMessage<Message, Channel> {
    /**
     * Whether any thread has been created for this message.
     */
    val hasThread: Boolean

    /**
     * Create a thread (channel) for a selected message.
     *
     * @return PNFuture that returns a ThreadChannel object which can be used for sending and reading messages from the newly created message thread.
     */
    fun createThread(): PNFuture<ThreadChannel>

    /**
     * Removes a thread (channel) for a selected message.
     *
     * @return A pair of values containing an object with details about the result of the remove message action (indicating whether the message was successfully removed and potentially including additional metadata or information about the removal) and the updated channel object after the removal of the thread.
     */
    fun removeThread(): PNFuture<Pair<PNRemoveMessageActionResult, ThreadChannel?>>

    /**
     * Get the thread channel on which the thread message is published.
     *
     * @return PNFuture that returns a ThreadChannel object which can be used for sending and reading messages from the message thread.
     */
    fun getThread(): PNFuture<ThreadChannel>

    override fun toggleReaction(reaction: String): PNFuture<Message>

    override fun streamUpdates(callback: (message: Message) -> Unit): AutoCloseable

    override fun restore(): PNFuture<Message>

    override fun pin(): PNFuture<Channel>

    companion object
}
