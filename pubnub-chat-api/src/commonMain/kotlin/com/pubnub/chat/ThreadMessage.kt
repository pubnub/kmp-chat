package com.pubnub.chat

import com.pubnub.kmp.PNFuture

/**
 * Represents a single message in a thread.
 */
interface ThreadMessage : BaseMessage<ThreadMessage, ThreadChannel> {
    /**
     * Unique identifier of the main channel on which you create a subchannel (thread channel) and thread messages.
     */
    val parentChannelId: String

    /**
     * Pins the thread message to the parent channel. This action updates the parent channel's metadata
     * with the following fields:
     * - `pinnedMessageTimetoken`: The timetoken marking when the message was pinned.
     * - `pinnedMessageChannelID`: The ID of the channel where the message was pinned (either the parent channel or a thread channel).
     *
     * @return [PNFuture] containing the updated [Channel] with the pinned message metadata.
     */
    fun pinToParentChannel(): PNFuture<Channel>

    /**
     * Unpins the thread message from the parent channel. This action updates the parent channel's metadata,
     * removing the pinned message information:
     * - `pinnedMessageTimetoken`: The timetoken marking when the message was pinned.
     * - `pinnedMessageChannelID`: The ID of the channel where the message was pinned (either the parent channel or a thread channel).
     *
     * @return [PNFuture] containing the updated [Channel] after the message is unpinned.
     */
    fun unpinFromParentChannel(): PNFuture<Channel>

    override fun toggleReaction(reaction: String): PNFuture<ThreadMessage>

    override fun streamUpdates(callback: (message: ThreadMessage) -> Unit): AutoCloseable

    override fun restore(): PNFuture<ThreadMessage>

    override fun pin(): PNFuture<ThreadChannel>

    companion object
}
