package com.pubnub.chat

import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.HistoryResponse
import com.pubnub.kmp.CustomObject
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
     * Fetches the message that is currently pinned to the channel. There can be only one pinned message on a channel at a time.
     *
     * @return [PNFuture] containing pinned [ThreadMessage]
     */
    override fun getPinnedMessage(): PNFuture<ThreadMessage?>

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
     * Allows to update the [Channel] metadata
     *
     * @param name Display name for the channel.
     * @param custom Any custom properties or metadata associated with the channel in the form of a `Map`.
     * Values must be scalar only; arrays or objects are not supported.
     * @param description Additional details about the channel.
     * @param status Current status of the channel, like online, offline, or archived.
     * @param type Represents the type of channel, which can be one of the following:
     *       - `ChannelType.DIRECT`: A one-on-one chat between two participants.
     *       - `ChannelType.GROUP`: A private group chat restricted to invited participants.
     *       - `ChannelType.PUBLIC`: A public chat open to a large audience, where anyone can join.
     *       - `ChannelType.UNKNOWN`: Used for channels created with the Kotlin SDK, where the channel type
     *         in the metadata does not match any of the three default Chat SDK types.
     *
     * @return [PNFuture] containing the updated [ThreadChannel] object with its metadata.
     */
    override fun update(
        name: String?,
        custom: CustomObject?,
        description: String?,
        status: String?,
        type: ChannelType?
    ): PNFuture<ThreadChannel>

    /**
     * Fetches the [Message] from Message Persistence based on the message [Message.timetoken].
     *
     * @param timetoken of the message you want to retrieve from Message Persistence
     *
     * @return [PNFuture] containing [ThreadMessage]
     */
    override fun getMessage(timetoken: Long): PNFuture<ThreadMessage?>

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
