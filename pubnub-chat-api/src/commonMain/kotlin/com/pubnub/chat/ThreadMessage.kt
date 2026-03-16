package com.pubnub.chat

import com.pubnub.kmp.PNFuture

/**
 * Represents a single message in a thread.
 */
interface ThreadMessage : Message {
    /**
     * Unique identifier of the main channel on which you create a subchannel (thread channel) and thread messages.
     */
    val parentChannelId: String

    /**
     * Changes the content of the existing message to a new one.
     *
     * @param newText New/updated text that you want to add in place of the existing message.
     * @return An updated message instance with an added `edited` action type.
     */
    override fun editText(newText: String): PNFuture<ThreadMessage>

    /**
     * Add or remove a reaction to a message.
     *
     * `toggleReaction()` is a method for both adding and removing message reactions. It adds a string flag to the message if the current user hasn't added it yet or removes it if the current user already added it before.
     *
     * If you use this method to add or remove message reactions, this flag would be a literal emoji you could implement in your app's UI. However, you could also use this method for a different purpose, like marking a message as pinned to a channel or unpinned if you implement the pinning feature in your chat app.
     *
     * @param reaction Emoji added to the message or removed from it by the current user.
     * @return Updated message instance with an added reactions action type.
     */
    override fun toggleReaction(reaction: String): PNFuture<ThreadMessage>

    /**
     * If you delete a message, you can restore its content together with the attached files using the restore() method.
     *
     * This is possible, however, only if the message you want to restore was soft deleted (the soft parameter was set to true when deleting it). Hard deleted messages cannot be restored as their data is no longer available in Message Persistence.
     *
     * Requires Message Persistence configuration. To manage messages, you must enable Message Persistence for your app's keyset in the Admin Portal and mark the Enable Delete-From-History option.
     *
     * @return Object returning the restored [ThreadMessage] object.
     */
    override fun restore(): PNFuture<ThreadMessage>

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

    /**
     * Emits the updated thread message entity whenever this message is edited, reactions are added or removed, or metadata changes.
     *
     * @param callback Function triggered with the updated [ThreadMessage] entity reflecting the new state.
     * @return [AutoCloseable] that stops receiving updates and cleans up resources when [AutoCloseable.close] is called.
     */
    fun onThreadMessageUpdated(callback: (message: ThreadMessage) -> Unit): AutoCloseable

    companion object
}
