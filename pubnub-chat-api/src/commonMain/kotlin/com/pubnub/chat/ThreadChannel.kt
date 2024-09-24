package com.pubnub.chat

import com.pubnub.chat.types.HistoryResponse
import com.pubnub.kmp.PNFuture

// todo add unit and integTests

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
    // TODO change parameter to ThreadMessage

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

    companion object
}
