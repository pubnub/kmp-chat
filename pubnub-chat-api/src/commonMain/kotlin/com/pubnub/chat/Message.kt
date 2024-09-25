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
interface Message {
    /**
     * Reference to the main Chat object.
     */
    val chat: Chat

    /**
     * Unique identifier for the message.
     */
    val timetoken: Long

    /**
     * Original text content of the message.
     */
    val content: EventContent.TextMessageContent

    /**
     * Unique identifier for the channel in which the message was sent.
     */
    val channelId: String

    /**
     * 	Unique ID of the user who sent the message.
     */
    val userId: String

    /**
     * Any actions associated with the message, such as reactions, replies, or other interactive elements.
     */
    val actions: Map<String, Map<String, List<Action>>>?

    /**
     * Extra information added to the message giving additional context.
     */
    val meta: Map<String, Any>?

    /**
     * Access the original quoted message.
     *
     * `quotedMessage` returns only values for the timetoken, text, and userId parameters. If you want to return the
     * full quoted Message object, use the [Channel.getMessage] method and the timetoken from the quote that you can
     * extract from the `quotedMessage` parameter added to the published message.
     */
    val quotedMessage: QuotedMessage?

    /**
     * Content of the message.
     */
    val text: String

    /**
     * Whether the message is soft deleted.
     */
    val deleted: Boolean

    /**
     * Whether any thread has been created for this message.
     */
    val hasThread: Boolean

    /**
     * Message type (currently "text" for all Messages).
     */
    val type: String

    /**
     * List of attached files with their names, types, and sources.
     */
    val files: List<File>

    /**
     * List of reactions attached to the message.
     */
    val reactions: Map<String, List<Action>>

    /**
     * List of included text links and their position.
     */
    @Deprecated("Use `mentions` instead.")
    val textLinks: List<TextLink>?

    /**
     * List of mentioned users with IDs and names.
     */
    @Deprecated("Use `mentions` instead.")
    val mentionedUsers: MessageMentionedUsers?

    /**
     * List of referenced channels with IDs and names.
     */
    @Deprecated("Use `mentions` instead.")
    val referencedChannels: MessageReferencedChannels?

    fun hasUserReaction(reaction: String): Boolean

    /**
     * Changes the content of the existing message to a new one.
     *
     * @param newText New/updated text that you want to add in place of the existing message.
     * @return An updated message instance with an added `edited` action type.
     */
    fun editText(newText: String): PNFuture<Message>

    /**
     * Either permanently removes a historical message from Message Persistence or marks it as deleted (if you remove the message with the soft option).
     *
     * Requires Message Persistence configuration. To manage messages, you must enable Message Persistence for your app's keyset in the Admin Portal. To delete messages from PubNub storage, you must also mark the Enable Delete-From-History option.
     *
     * @param soft Decide if you want to permanently remove message data. By default, the message data gets permanently deleted from Message Persistence. If you set this parameter to true, the Message object gets the deleted status and you can still restore/get its data.
     * @param preserveFiles Define if you want to keep the files attached to the message or remove them.
     * @return For hard delete, the method returns the last version of the Message object before it was permanently deleted. For soft delete, an updated message instance with an added deleted action type.
     */
    fun delete(soft: Boolean = false, preserveFiles: Boolean = false): PNFuture<Message?>

    /**
     * Get the thread channel on which the thread message is published.
     *
     * @return PNFuture that returns a ThreadChannel object which can be used for sending and reading messages from the message thread.
     */
    fun getThread(): PNFuture<ThreadChannel>

    /**
     * Forward a given message from one channel to another.
     *
     * @param channelId Unique identifier of the channel to which you want to forward the message. You can forward a message to the same channel on which it was published or to any other.
     * @return [PNFuture] containing [PNPublishResult] that holds the timetoken of the forwarded message.
     */
    fun forward(channelId: String): PNFuture<PNPublishResult>

    /**
     * Attach this message to its channel.
     *
     * @return `PNFuture` containing the updated channel metadata
     */
    fun pin(): PNFuture<Channel>

    // todo do we have test for this?

    /**
     * Flag and report an inappropriate message to the admin.
     *
     * @param reason Reason for reporting/flagging a given message.
     * @return [PNFuture] containing [PNPublishResult] that holds the timetoken of the report message.
     */
    fun report(reason: String): PNFuture<PNPublishResult>

    /**
     * Create a thread (channel) for a selected message.
     *
     * @return PNFuture that returns a ThreadChannel object which can be used for sending and reading messages from the newly created message thread.
     */
    fun createThread(): PNFuture<ThreadChannel>

    // todo do we have test for this?

    /**
     * Removes a thread (channel) for a selected message.
     *
     * @return A pair of values containing an object with details about the result of the remove message action (indicating whether the message was successfully removed and potentially including additional metadata or information about the removal) and the updated channel object after the removal of the thread.
     */
    fun removeThread(): PNFuture<Pair<PNRemoveMessageActionResult, Channel>>

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
    fun toggleReaction(reaction: String): PNFuture<Message>

    // todo do we want to have test for this?

    /**
     * You can receive updates when this message and related message reactions are added, edited, or removed.
     *
     * @param callback Function that takes a single Message object. It defines the custom behavior to be executed when detecting message or message reaction changes.
     * @return Interface that lets you stop receiving message-related updates by invoking the close() method
     */
    fun <T : Message> streamUpdates(callback: (message: T) -> Unit): AutoCloseable

    /**
     * If you delete a message, you can restore its content together with the attached files using the restore() method.
     *
     * This is possible, however, only if the message you want to restore was soft deleted (the soft parameter was set to true when deleting it). Hard deleted messages cannot be restored as their data is no longer available in Message Persistence.
     *
     * Requires Message Persistence configuration. To manage messages, you must enable Message Persistence for your app's keyset in the Admin Portal and mark the Enable Delete-From-History option.
     *
     * @return Object returning the restored Message object.
     */
    fun restore(): PNFuture<Message>

    companion object
}
