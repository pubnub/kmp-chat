package com.pubnub.chat

import com.pubnub.chat.types.HistoryResponse
import com.pubnub.kmp.PNFuture

/**
 * Represents an object that refers to a single thread (channel) in a chat.
 */
interface ThreadChannel : Channel {
    /**
     * Message for which the thread was created.
     */
    val parentMessage: Message

    /**
     * Unique identifier of the main channel on which you create a subchannel (thread channel) and thread messages.
     */
    val parentChannelId: String

    /**
     * Pins a selected thread message to the thread channel.
     *
     * @param message you want to pin to the selected thread channel.
     *
     * @return [PNFuture] containing [ThreadChannel]
     */
    override fun pinMessage(message: Message): PNFuture<ThreadChannel>

    /**
     * Unpins the previously pinned thread message from the thread channel.
     *
     * @return [PNFuture] containing [ThreadChannel]
     */
    override fun unpinMessage(): PNFuture<ThreadChannel>

    /**
     *  Returns historical messages for the [ThreadChannel]
     *
     *  @param startTimetoken
     *  @param endTimetoken
     *  @param count The maximum number of messages to retrieve. Default and maximum values is 25.
     *
     *  @return [PNFuture] containing a list of messages with pagination information (isMore: Boolean). The result of
     *  this future can be processed using the `async` method of `PNFuture`.
     */
    override fun getHistory(startTimetoken: Long?, endTimetoken: Long?, count: Int): PNFuture<HistoryResponse<ThreadMessage>>

    /**
     * Pins a selected thread message to the parent channel. This updates the parent channel's metadata with the
     * following fields:
     * - `pinnedMessageTimetoken`: The timetoken marking when the message was pinned.
     * - `pinnedMessageChannelID`: The ID of the channel where the message was pinned (either the parent channel or the thread channel).
     *
     * @param message The [ThreadMessage] to pin to the parent channel.
     *
     * @return [PNFuture] containing the updated [Channel] with the pinned message metadata.
     */
    fun pinMessageToParentChannel(message: ThreadMessage): PNFuture<Channel>

    /**
     * Unpins the currently pinned message from the parent channel. This updates the parent channel's metadata by removing
     * the pinned message information:
     * - `pinnedMessageTimetoken`: The timetoken marking when the message was pinned.
     * - `pinnedMessageChannelID`: The ID of the channel where the message was pinned (either the parent channel or the thread channel).
     *
     * @return [PNFuture] containing the updated [Channel] after the message is unpinned.
     */
    fun unpinMessageFromParentChannel(): PNFuture<Channel>

    /**
     * Registers a callback to receive messages sent to this thread channel as [ThreadMessage] instances,
     * preserving thread context such as [ThreadMessage.parentChannelId].
     *
     * Unlike [Channel.onMessageReceived], which returns plain [Message] objects, this method ensures
     * the callback receives properly-typed [ThreadMessage] objects.
     *
     * @param callback Function invoked with each incoming [ThreadMessage].
     *
     * @return [AutoCloseable] that stops listening for new thread messages when [AutoCloseable.close] is called.
     */
    fun onThreadMessageReceived(callback: (ThreadMessage) -> Unit): AutoCloseable

    /**
     * Registers a callback that is invoked whenever this thread channel's metadata is updated,
     * returning the updated entity as a [ThreadChannel].
     *
     * Unlike [Channel.onUpdated], which returns a plain [Channel], this method ensures
     * the callback receives a properly-typed [ThreadChannel] with thread context preserved.
     *
     * @param callback Function invoked with the updated [ThreadChannel].
     *
     * @return [AutoCloseable] that stops listening for thread channel updates when [AutoCloseable.close] is called.
     */
    fun onThreadChannelUpdated(callback: (ThreadChannel) -> Unit): AutoCloseable

    companion object
}
