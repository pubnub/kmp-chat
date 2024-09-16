package com.pubnub.chat

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.files.PNDeleteFileResult
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.types.GetFilesResult
import com.pubnub.chat.types.HistoryResponse
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.JoinResult
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

/**
 * Channel is an object that refers to a single chat room.
 */
interface Channel {
    /**
     * Reference to the main Chat object.
     */
    val chat: Chat

    /**
     * Unique identifier for the channel.
     */
    val id: String

    /**
     * 	Display name or title of the channel.
     */
    val name: String?

    /**
     * Any custom properties or metadata associated with the channel in the form of a map of key-value pairs.
     */
    val custom: Map<String, Any?>?

    /**
     * Brief description or summary of the channel's purpose or content.
     */
    val description: String?

    /**
     * Timestamp for the last time the channel was updated or modified.
     */
    val updated: String?

    /**
     * Current status of the channel, like online, offline, or archived.
     */
    val status: String?

    /**
     * Represents the type of channel, which can be one of the following:
     *
     * - `ChannelType.DIRECT`: A one-on-one chat between two participants.
     * - `ChannelType.GROUP`: A private group chat restricted to invited participants.
     * - `ChannelType.PUBLIC`: A public chat open to a large audience, where anyone can join.
     * - `ChannelType.UNKNOWN`: Used for channels created with the Kotlin SDK, where the channel type
     *   in the metadata does not match any of the three default Chat SDK types.
     */
    val type: ChannelType?

    /**
     * Allows to update the [Channel] metadata
     *
     * @param name Display name for the channel.
     * @param custom JSON object providing custom data about the channel. Values must be scalar only; arrays or objects are not supported.
     * @param description Additional details about the channel.
     * @param status Current status of the channel, like online, offline, or archived.
     * @param type Represents the type of channel, which can be one of the following:
     *       - `ChannelType.DIRECT`: A one-on-one chat between two participants.
     *       - `ChannelType.GROUP`: A private group chat restricted to invited participants.
     *       - `ChannelType.PUBLIC`: A public chat open to a large audience, where anyone can join.
     *       - `ChannelType.UNKNOWN`: Used for channels created with the Kotlin SDK, where the channel type
     *         in the metadata does not match any of the three default Chat SDK types.
     *
     * @return [PNFuture] containing the updated the [Channel] object with its metadata.
     */
    fun update(
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        status: String? = null,
        type: ChannelType? = null,
    ): PNFuture<Channel>

    /**
     * Allows to delete  an existing [Channel] (with or without deleting its historical data from the App Context storage)
     *
     * @param soft Define if you want to permanently remove channel metadata. The channel metadata gets permanently
     *             deleted from the App Context storage by default. If you set this parameter to true, the Channel object
     *             gets the deleted status, and you can still restore/get its data.
     * @return For hard delete, the method returns [PNFuture] with the last version of the [Channel] object before it was permanently deleted.
     *         For soft delete, [PNFuture] containing an updated [Channel] instance with the status field set to "deleted".
     */
    fun delete(soft: Boolean = false): PNFuture<Channel>

    /**
     * Forwards message to existing [Channel]
     *
     * @param message Message object that you want to forward to the [Channel].
     *
     * @return [PNFuture] containing [PNPublishResult] that holds the timetoken of the forwarded message.
     */
    fun forwardMessage(message: Message): PNFuture<PNPublishResult>

    /**
     * Activates a typing indicator on a given channel.
     * The method sets a flag (typingSent) to indicate that a typing signal is in progress and adds a timer to reset
     * the flag after a specified timeout.
     *
     * You can change the default typing timeout and set your own value during the Chat SDK configuration (init() method)
     * using the [com.pubnub.chat.config.ChatConfiguration.typingTimeout] parameter.
     *
     * @return [PNFuture] representing the typing action. The result of this future
     *         can be handled using the `async` method of `PNFuture`.
     *
     * Example usage:
     * ```
     * channel.startTyping().async { result ->
     *     result.onSuccess {
     *         // Handle success
     *     }.onFailure { error ->
     *         // Handle error
     *     }
     * }
     * ```
     */
    fun startTyping(): PNFuture<Unit>

    /**
     * Deactivates a typing indicator on a given channel.
     *
     * @return [PNFuture] representing the stop typing action. The result of this future
     *         can be handled using the `async` method of `PNFuture`.
     *
     * Example usage:
     * ```
     * channel.stopTyping().async { result ->
     *     result.onSuccess {
     *         // Handle success
     *     }.onFailure { error ->
     *         // Handle error
     *     }
     * }
     * ```
     */
    fun stopTyping(): PNFuture<Unit>

    /**
     * Enables continuous tracking of typing activity within the [Channel].
     *
     * @param callback Callback function passed as a parameter. It defines the custom behavior to be executed whenever
     * a user starts/stops typing.
     *
     * @return AutoCloseable Interface you can call to disconnect (unsubscribe) from the channel and stop receiving
     * signal events for someone typing by invoking the close() method.
     */
    fun getTyping(callback: (typingUserIds: Collection<String>) -> Unit): AutoCloseable

    /**
     * Returns a list of users present on the [Channel]
     *
     * @return [PNFuture] A future containing a collection of strings representing userId.
     *                    The result of this future can be processed using the `async` method of `PNFuture`.
     *
     */
    fun whoIsPresent(): PNFuture<Collection<String>> // todo do we have an integTest for this ?

    /**
     * Returns information if the user is present on the [Channel]
     *
     * @param userId ID of the user whose presence you want to check.
     *
     * @return [PNFuture] with Boolean value informing if a given user is present on a specified [Channel]
     */
    fun isPresent(userId: String): PNFuture<Boolean> // todo do we have an integTest for this ?

    /**
     *  Returns historical messages for the [Channel]
     *
     *  @param startTimetoken
     *  @param endTimetoken
     *  @param count The maximum number of messages to retrieve. Defaults and maximum values is 25.
     *
     *  @return [PNFuture] containing a list of messages with pagination information (isMore: Boolean). The result of
     *  this future can be processed using the `async` method of `PNFuture`.
     */
    fun getHistory(
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 25,
    ): PNFuture<HistoryResponse<*>>

    /**
     * Sends text to the [Channel]
     *
     * @param text Text that you want to send to the selected channel.
     * @param meta Publish additional details with the request.
     * @param shouldStore If true, the messages are stored in Message Persistence if enabled in Admin Portal.
     * @param usePost Use HTTP POST
     * @param ttl Defines if / how long (in hours) the message should be stored in Message Persistence.
     * If shouldStore = true, and ttl = 0, the message is stored with no expiry time.
     * If shouldStore = true and ttl = X, the message is stored with an expiry time of X hours.
     * If shouldStore = false, the ttl parameter is ignored.
     * If ttl is not specified, then the expiration of the message defaults back to the expiry value for the keyset.
     * @param mentionedUsers Object mapping a mentioned user (with name and ID) with the number of mention (like @Mar)
     * in the message (relative to other user mentions).
     * For example, { 0: { id: 123, name: "Mark" }, 2: { id: 345, name: "Rob" } } means that Mark will be shown on
     * the first mention (@) in the message and Rob on the third.
     * @param referencedChannels Object mapping the referenced channel (with name and ID) with the place (Int) where
     * this reference (like #Sup) was mentioned in the message (relative to other channel references).
     * For example, { 0: { id: 123, name: "Support" }, 2: { id: 345, name: "Off-topic" } } means that Support will be
     * shown on the first reference in the message and Off-topic on the third.
     * @param textLinks Returned list of text links that are shown as text in the message Each TextLink contains these
     * fields: class TextLink(val startIndex: Int, val endIndex: Int, val link: String) where startIndex indicates
     * the position in the whole message where the link should start and endIndex where it ends. Note that indexing starts with 0.
     * @param quotedMessage Object added to a message when you quote another message. This object stores the following
     * info about the quoted message: timetoken for the time when the quoted message was published, text with the
     * original message content, and userId as the identifier of the user who published the quoted message.
     * @param files One or multiple files attached to the text message.
     *
     * @return [PNFuture] containing [PNPublishResult] that holds the timetoken of the sent message.
     */
    fun sendText(
        text: String,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean = true,
        usePost: Boolean = false,
        ttl: Int? = null,
        mentionedUsers: MessageMentionedUsers? = null,
        referencedChannels: MessageReferencedChannels? = null,
        textLinks: List<TextLink>? = null,
        quotedMessage: Message? = null,
        files: List<InputFile>? = null,
    ): PNFuture<PNPublishResult>

    /**
     * Requests another user to join a channel(except Public channel) and become its member.
     *
     * @param user that you want to invite to a channel.
     *
     * @return [PNFuture] containing [Membership]
     */
    fun invite(user: User): PNFuture<Membership>

    /**
     * Requests other users to join a channel and become its members. You can invite up to 100 users at once.
     *
     * @param users List of users you want to invite to the [Channel]. You can invite up to 100 users in one call.
     *
     * @return [PNFuture] containing list of [Membership] of invited users.
     */
    fun inviteMultiple(users: Collection<User>): PNFuture<List<Membership>>

    /**
     * Returns the list of all channel members.
     *
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     * @param filter Expression used to filter the results. Returns only these members whose properties satisfy the given expression.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc
     * to specify the sorting direction, or specify null to take the default sorting direction (ascending).
     *
     * @return [PNFuture] containing [MembersResponse]
     */
    fun getMembers(
        limit: Int? = 100,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMemberKey>> = listOf(),
    ): PNFuture<MembersResponse>

    /**
     * Watch the [Channel] content without a need to [join] the [Channel]
     *
     * @param callback defines the custom behavior to be executed whenever a message is received on the [Channel]
     *
     * @return AutoCloseable Interface you can call to stop listening for new messages and clean up resources when they
     * are no longer needed by invoking the close() method.
     */
    fun connect(callback: (Message) -> Unit): AutoCloseable

    /**
     * Connects a user to the [Channel] and sets membership - this way, the chat user can both watch the channel's
     * content and be its full-fledged member.
     *
     * @param custom Any custom properties or metadata associated with the channel-user membership in the form of
     *      * a JSON. Values must be scalar only; arrays or objects are not supported.
     * @param callback defines the custom behavior to be executed whenever a message is received on the [Channel]
     *
     * @return [PNFuture] containing [JoinResult] that contains the [JoinResult.membership] and
     * [JoinResult.disconnect] that  lets you stop listening to new channel messages or message updates while remaining
     * a channel membership. This might be useful when you want to stop receiving notifications about new messages or
     * limit incoming messages or updates to reduce network traffic.
     */
    fun join(custom: CustomObject? = null, callback: ((Message) -> Unit)? = null): PNFuture<JoinResult>

    /**
     * Remove user's [Channel] membership
     */
    fun leave(): PNFuture<Unit>

    /**
     * Fetches the message that is currently pinned to the channel. There can be only one pinned message on a channel at a time.
     *
     * @return [PNFuture] containing pinned [Message]
     */
    fun getPinnedMessage(): PNFuture<Message?>

    /**
     * Attaches messages to the [Channel]. Replace an already pinned message. There can be only one pinned message on a channel at a time.
     *
     * @param message that you want to pin to the selected channel.
     *
     * @return [PNFuture] containing updated [Channel.custom]
     */
    fun pinMessage(message: Message): PNFuture<Channel>

    /**
     * Unpins a message from the [Channel].
     *
     * @return [PNFuture] containing updated [Channel.custom]
     */
    fun unpinMessage(): PNFuture<Channel>

    /**
     * Fetches the [Message] from Message Persistence based on the message [Message.timetoken].
     *
     * @param timetoken of the message you want to retrieve from Message Persistence
     *
     * @return [PNFuture] containing [Message]
     */
    fun getMessage(timetoken: Long): PNFuture<Message?>

    /**
     * Register a device on the [Channel] to receive push notifications. Push options can be configured in [com.pubnub.chat.config.ChatConfiguration]
     *
     * @return [PNFuture] containing [PNPushAddChannelResult]
     */
    fun registerForPush(): PNFuture<PNPushAddChannelResult>

    /**
     * Unregister a device from the [Channel]
     *
     * @return [PNFuture] containing [PNPushRemoveChannelResult]
     */
    fun unregisterFromPush(): PNFuture<PNPushRemoveChannelResult>

    /**
     * Allows to mute/ban a specific user on a channel or unmute/unban them.
     *
     * @param user to be muted or banned.
     * @param ban represents the user's moderation restrictions. Set to true to ban the user from the channel or to false to unban them.
     * @param mute represents the user's moderation restrictions. Set to true to mute the user on the channel or to false to unmute them.
     * @param reason Reason why you want to ban/mute the user.
     *
     * @return [PNFuture] that will be completed with Unit.
     */
    fun setRestrictions(
        user: User,
        ban: Boolean = false,
        mute: Boolean = false,
        reason: String? = null
    ): PNFuture<Unit>

    /**
     * Check user's restrictions.
     *
     * @param user to be checked permission for.
     *
     * @return [PNFuture] containing [Restriction]
     */
    fun getUserRestrictions(user: User): PNFuture<Restriction>

    /**
     * Check if there are any mute or ban restrictions set for user on a given channel
     *
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc
     * to specify the sorting direction, or specify null to take the default sorting direction (ascending).
     *
     * @return [PNFuture] containing [GetRestrictionsResponse]
     */
    fun getUsersRestrictions(
        limit: Int? = null,
        page: PNPage? = null,
        sort: Collection<PNSortKey<PNMemberKey>> = listOf()
    ): PNFuture<GetRestrictionsResponse>

    /**
     * Checks updates on a single Channel object.
     *
     * @param callback Function that takes a single Channel object. It defines the custom behavior to be executed when detecting channel changes.
     *
     * @return AutoCloseable Interface that lets you stop receiving channel-related updates (objects events)
     * and clean up resources by invoking the close() method.
     */
    fun streamUpdates(callback: (channel: Channel?) -> Unit): AutoCloseable

    /**
     * Lets you get a read confirmation status for messages you published on a channel.
     * @param callback defines the custom behavior to be executed when receiving a read confirmation status on the joined channel.
     *
     * @return AutoCloseable Interface you can call to stop listening for message read receipts
     * and clean up resources by invoking the close() method.
     */
    fun streamReadReceipts(callback: (receipts: Map<Long, List<String>>) -> Unit): AutoCloseable

    /**
     * Returns all files attached to messages on a given channel.
     *
     * @param limit Number of files to return(default and max is 100)
     * @param next Token to get the next batch of files.
     *
     * @return [PNFuture] containing [GetFilesResult]
     */
    fun getFiles(limit: Int = 100, next: String? = null): PNFuture<GetFilesResult> // todo add tests for this

    /**
     * Delete sent files or files from published messages.
     *
     * @param id Unique identifier assigned to the file by PubNub.
     * @param name Name of the file.
     *
     * @return [PNFuture] containing [PNDeleteFileResult]
     */
    fun deleteFile(id: String, name: String): PNFuture<PNDeleteFileResult> // todo add tests for this

    /**
     * Enables real-time tracking of users connecting to or disconnecting from a [Channel].

     * @param callback defines the custom behavior to be executed when detecting user presence event.
     *
     * @return AutoCloseable Interface that lets you stop receiving presence-related updates (presence events) by invoking the close() method.
     */
    fun streamPresence(callback: (userIds: Collection<String>) -> Unit): AutoCloseable

    /**
     * Fetches all suggested users that match the provided 3-letter string from [Channel]
     *
     * @param text at least a 3-letter string typed in after @ with the user name you want to mention.
     * @param limit maximum number of returned usernames that match the typed 3-letter suggestion. The default value is set to 10, and the maximum is 100.
     *
     * @return [PNFuture] containing set of [Membership]
     */
    fun getUserSuggestions(text: String, limit: Int = 10): PNFuture<Set<Membership>>

    /**
     * Fetches a list of reported message events for [Channel] within optional time and count constraints.
     *
     * @param startTimetoken The start timetoken for fetching the history of reported messages, which allows specifying
     *                       the point in time where the history retrieval should begin.
     * @param endTimetoken   The end time token for fetching the history of reported messages, which allows specifying
     *                       the point in time where the history retrieval should end.
     * @param count          The number of reported message events to fetch from the history. Default and max is 100.
     */
    fun getMessageReportsHistory(
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 100,
    ): PNFuture<GetEventsHistoryResult>

    /**
     * As an admin of your chat app, monitor all events emitted when someone reports an offensive message.
     *
     * @param callback Callback function passed as a parameter. It defines the custom behavior to be executed when
     *                 detecting new message report events.
     *
     * @return AutoCloseable Interface that lets you stop receiving report-related updates (report events) by invoking the close() method.
     */
    fun streamMessageReports(callback: (event: Event<EventContent.Report>) -> Unit): AutoCloseable

    /**
     * Get a new `Channel` instance that is a copy of this `Channel` with its properties updated with information coming from `update`.
     */
    operator fun plus(update: PNChannelMetadata): Channel

    // Companion object required for extending this class elsewhere
    companion object
}
