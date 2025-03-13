package com.pubnub.chat

import com.pubnub.chat.types.HistoryResponse
import com.pubnub.kmp.PNFuture

/**
 * Represents an object that refers to a single thread (channel) in a chat.
 */
interface ThreadChannel : BaseChannel<ThreadChannel, ThreadMessage> {
    /**
     * Message for which the thread was created.
     */
    val parentMessage: Message

    /**
     * Unique identifier of the main channel on which you create a subchannel (thread channel) and thread messages.
     */
    val parentChannelId: String

    override fun pinMessage(message: BaseMessage<*, *>): PNFuture<ThreadChannel>

    override fun unpinMessage(): PNFuture<ThreadChannel>

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
