package com.pubnub.chat

import com.pubnub.api.PubNubError
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.PNFuture

/**
 * Represents an object that refers to a single message in a chat.
 */
interface BaseMessage<M : BaseMessage<M, C>, C : BaseChannel<C, M>> {
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
     * Error associated with the message, if any.
     */
    val error: PubNubError?

    /**
     * List of included text links and their position.
     */
    @Deprecated("Use `Message.getMessageElements()` instead.")
    val textLinks: List<TextLink>?

    /**
     * List of mentioned users with IDs and names.
     */
    @Deprecated("Use `Message.getMessageElements()` instead.")
    val mentionedUsers: MessageMentionedUsers?

    /**
     * List of referenced channels with IDs and names.
     */
    @Deprecated("Use `Message.getMessageElements()` instead.")
    val referencedChannels: MessageReferencedChannels?

    /**
     * Checks if the current user added a given emoji to the message.
     *
     * @param reaction Specific emoji added to the message.
     * @return Specifies if the current user added a given emoji to the message or not.
     */
    fun hasUserReaction(reaction: String): Boolean

    /**
     * Changes the content of the existing message to a new one.
     *
     * @param newText New/updated text that you want to add in place of the existing message.
     * @return An updated message instance with an added `edited` action type.
     */
    fun editText(newText: String): PNFuture<M>

    /**
     * Either permanently removes a historical message from Message Persistence or marks it as deleted (if you remove the message with the soft option).
     *
     * Requires Message Persistence configuration. To manage messages, you must enable Message Persistence for your app's keyset in the Admin Portal. To delete messages from PubNub storage, you must also mark the Enable Delete-From-History option.
     *
     * @param soft Decide if you want to permanently remove message data. By default, the message data gets permanently deleted from Message Persistence. If you set this parameter to true, the Message object gets the deleted status and you can still restore/get its data.
     * @param preserveFiles Define if you want to keep the files attached to the message or remove them.
     * @return For hard delete, the method returns `PNFuture` without a value (`null`). For soft delete, a `PNFuture` with an updated message instance with an added deleted action type.
     */
    fun delete(soft: Boolean = false, preserveFiles: Boolean = false): PNFuture<M?>

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
    fun pin(): PNFuture<C>

    /**
     * Flag and report an inappropriate message to the admin.
     *
     * @param reason Reason for reporting/flagging a given message.
     * @return [PNFuture] containing [PNPublishResult] that holds the timetoken of the report message.
     */
    fun report(reason: String): PNFuture<PNPublishResult>

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
    fun toggleReaction(reaction: String): PNFuture<M>

    /**
     * You can receive updates when this message and related message reactions are added, edited, or removed.
     *
     * @param callback Function that takes a single Message object. It defines the custom behavior to be executed when detecting message or message reaction changes.
     * @return Interface that lets you stop receiving message-related updates by invoking the close() method
     */
    fun streamUpdates(callback: (message: M) -> Unit): AutoCloseable

    /**
     * If you delete a message, you can restore its content together with the attached files using the restore() method.
     *
     * This is possible, however, only if the message you want to restore was soft deleted (the soft parameter was set to true when deleting it). Hard deleted messages cannot be restored as their data is no longer available in Message Persistence.
     *
     * Requires Message Persistence configuration. To manage messages, you must enable Message Persistence for your app's keyset in the Admin Portal and mark the Enable Delete-From-History option.
     *
     * @return Object returning the restored Message object.
     */
    fun restore(): PNFuture<M>

    companion object
}
