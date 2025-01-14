package com.pubnub.chat

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.message.MarkAllMessageAsReadResponse
import com.pubnub.chat.mutelist.MutedUsersManager
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateDirectConversationResult
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.types.GetCurrentUserMentionsResult
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import kotlin.reflect.KClass

/**
 * To communicate with PubNub, you can use various methods on [Chat] object. For example, you can use deleteChannel()
 * to remove a given channel or wherePresent() to check which channels a given user is subscribed to.
 *
 * By calling methods on the Chat entity, you create chat objects like Channel, User, Message, Membership, ThreadChannel,
 * and ThreadMessage. These objects also expose Chat API under various methods, letting you perform CRUD operations
 * on messages, channels, users, the related user-channel membership, and many more.
 */
interface Chat {
    /**
     * Contains chat app configuration settings, such as [ChatConfiguration.logLevel] or [ChatConfiguration.typingTimeout]
     * that you can provide when initializing your chat app with the init() method. You can later directly access
     * these properties like: chat.storeUserActivityInterval.
     */
    val config: ChatConfiguration

    /**
     * Allows you to access any Kotlin SDK method. For example, if you want to call a method available in the
     * App Context API, you'd use chat.pubNub.getAllUUIDMetadata().
     */
    val pubNub: PubNub

    /**
     * [User] object representing current user.
     */
    val currentUser: User

    /**
     * An object for manipulating the list of muted users.
     *
     * The list is local to this instance of Chat (it is not persisted anywhere) unless
     * [ChatConfiguration.syncMutedUsers] is enabled, in which case it will be synced using App Context for the current
     * user.
     *
     * Please note that this is not a server-side moderation mechanism (use [Chat.setRestrictions] for that), but rather
     * a way to ignore messages from certain users on the client.
     *
     * @see ChatConfiguration.syncMutedUsers
     * @see MutedUsersManager
     */
    val mutedUsersManager: MutedUsersManager

    /**
     * Creates a new [User] with a unique User ID.
     *
     * @param id Unique user identifier. A User ID is a UTF-8 encoded, unique string of up to 92 characters used to
     * identify a single client (end user, device, or server).
     * @param name Display name for the user (must not be empty or consist only of whitespace characters).
     * @param externalId User's identifier in an external system. You can use it to match id with a similar identifier from an external database.
     * @param profileUrl URL of the user's profile picture.
     * @param email User's email address.
     * @param custom Any custom properties or metadata associated with the user in the form of a `Map`.
     * Values must be scalar only; arrays or objects are not supported.
     * @param status Tag that lets you categorize your app users by their current state. The tag choice is entirely up
     * to you and depends on your use case. For example, you can use status to mark users in your chat app as invited,
     * active, or archived.
     * @param type Tag that lets you categorize your app users by their functional roles. The tag choice is entirely up
     * to you and depends on your use case. For example, you can use type to group users by their roles in your app, such as moderator, player, or support-agent.
     *
     * @return [PNFuture] containing newly create [User] object.
     */
    fun createUser(
        id: String,
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
    ): PNFuture<User>

    /**
     * Returns data about a specific user.
     *
     * @param userId Unique user identifier (up to 92 UTF-8 characters).
     *
     * @return [PNFuture] containing [User] object.
     */
    fun getUser(userId: String): PNFuture<User?>

    /**
     * Returns a paginated list of all users and their details
     *
     * @param filter Expression used to filter the results. Returns only these users whose properties satisfy the
     * given expression are returned. The filtering language is defined in [documentation](https://www.pubnub.com/docs/general/metadata/filtering).
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     *
     * @return [PNFuture] containing a set of users with pagination information (next, prev, total).
     */
    fun getUsers(
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        limit: Int? = null,
        page: PNPage? = null,
    ): PNFuture<GetUsersResponse>

    /**
     * Updates metadata of [User]
     *
     * @param id Unique user identifier. A User ID is a UTF-8 encoded, unique string of up to 92 characters used to
     * identify a single client (end user, device, or server).
     * @param name Display name for the user (must not be empty or consist only of whitespace characters).
     * @param externalId User's identifier in an external system. You can use it to match id with a similar identifier from an external database.
     * @param profileUrl URL of the user's profile picture.
     * @param email User's email address.
     * @param custom Any custom properties or metadata associated with the user in the form of a `Map`.
     * Values must be scalar only; arrays or objects are not supported.
     * @param status Tag that lets you categorize your app users by their current state. The tag choice is entirely up
     * to you and depends on your use case. For example, you can use status to mark users in your chat app as invited,
     * active, or archived.
     * @param type Tag that lets you categorize your app users by their functional roles. The tag choice is entirely up
     * to you and depends on your use case. For example, you can use type to group users by their roles in your app, such as moderator, player, or support-agent.
     *
     * @return [PNFuture] containing updated [User] object.
     */
    fun updateUser(
        id: String,
        // TODO change nulls to Optionals when there is support. In Kotlin SDK there should be possibility to handle PatchValue
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
    ): PNFuture<User>

    /**
     * Deletes a [User] with or without deleting its historical data from the App Context storage.
     *
     * @param id Unique user identifier.
     * @param soft Decide if you want to permanently remove user metadata. The user metadata gets permanently deleted
     * from the App Context storage by default. If you set this parameter to true, the User object gets the deleted
     * status, and you can still restore/get their metadata.
     *
     * @return For hard delete, the method returns [PNFuture] without a value (`null`).
     * For soft delete, [PNFuture] containing an updated [User] instance with the status field set to "deleted".
     */
    fun deleteUser(id: String, soft: Boolean = false): PNFuture<User?>

    /**
     * Retrieves list of [Channel.id] where a given user is present.
     *
     * @param userId Unique user identifier.
     *
     * @return [PNFuture] containing list of [Channel.id] object.
     */
    fun wherePresent(userId: String): PNFuture<List<String>>

    /**
     *  Returns information if the user is present on a specified channel.
     *
     * @param userId Unique user identifier.
     * @param channelId Unique identifier of the channel where you want to check the user's presence.
     *
     * @return [PNFuture] containing information on whether a given user is present on a specified channel (true) or not (false).
     */
    fun isPresent(userId: String, channelId: String): PNFuture<Boolean>

    /**
     * Fetches details of a specific channel.
     *
     * @param channelId Unique channel identifier (up to 92 UTF-8 byte sequences).
     *
     * @return [PNFuture] containing [Channel] object with its metadata.
     */
    fun getChannel(channelId: String): PNFuture<Channel?>

    /**
     * Returns a paginated list of all existing channels.
     *
     * @param filter Expression used to filter the results. Returns only these channels whose properties satisfy the given expression are returned.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc.
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     *
     * @return [PNFuture] containing [GetChannelsResponse].
     */
    fun getChannels(
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        limit: Int? = null,
        page: PNPage? = null,
    ): PNFuture<GetChannelsResponse>

    /**
     * Allows to update the [Channel] metadata
     *
     * @param id Unique channel identifier.
     * @param name Display name for the channel.
     * @param custom Any custom properties or metadata associated with the channel in the form of a `Map`.
     * Values must be scalar only; arrays or objects are not supported.
     * @param description Additional details about the channel.
     * @param status Tag that categorizes a channel by its state, like archived.
     * @param type Tag that categorizes a channel by its function, like offtopic.
     *
     * @return
     */
    fun updateChannel(
        id: String,
        // TODO change nulls to Optionals when there is support. In Kotlin SDK there should be possibility to send PatchValue to sever.
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        status: String? = null,
        type: ChannelType? = null,
    ): PNFuture<Channel>

    /**
     * Allows to delete [Channel] (with or without deleting its historical data from the App Context storage)
     *
     * @param id Unique channel identifier (up to 92 UTF-8 byte sequences).
     * @param soft Decide if you want to permanently remove channel metadata. The channel metadata gets permanently
     * deleted from the App Context storage by default. If you set this parameter to true, the Channel object
     * gets the deleted status, and you can still restore/get its data.
     *
     * @return For hard delete, the method returns [PNFuture] without a value (`null`).
     *         For soft delete, [PNFuture] containing an updated [Channel] instance with the status field set to "deleted".
     */
    fun deleteChannel(id: String, soft: Boolean = false): PNFuture<Channel?>

    /**
     * Returns a list of [User.id] present on the given [Channel].
     *
     * @param channelId Unique identifier of the channel where you want to check all present users.
     *
     * @return [PNFuture] containing Collection of [User.id].
     */
    fun whoIsPresent(channelId: String): PNFuture<Collection<String>>

    /**
     * Constructs and sends events with your custom payload.
     *
     * @param channelId Channel where you want to send the events.
     * @param payload The payload of the emitted event. Use one of [EventContent] subclasses, for example:
     * [EventContent.Mention], [EventContent.TextMessageContent] or [EventContent.Custom] for sending arbitrary data.
     * @param mergePayloadWith Metadata in the form of key-value pairs you want to pass as events from your chat app.
     * Can contain anything in case of custom events, but has a predefined structure for other types of events.
     *
     * @return [PNFuture] containing [PNPublishResult] that holds the timetoken of the emitted event.
     */
    fun <T : EventContent> emitEvent(
        channelId: String,
        payload: T,
        mergePayloadWith: Map<String, Any>? = null,
    ): PNFuture<PNPublishResult>

    /**
     * Creates a public channel that let users engage in open conversations with many people.
     * Unlike group chats, anyone can join public channels.
     *
     * @param channelId ID of the public channel. The channel ID is created automatically using the v4 UUID generator.
     * You can override it by providing your own ID.
     * @param channelName Display name for the channel.
     * If you don't provide the name, the channel will get the same name as id (value of channelId).
     * @param channelDescription Additional details about the channel.
     * @param channelCustom Any custom properties or metadata associated with the channel in the form of a map of key-value pairs.
     * @param channelStatus Current status of the channel, like online, offline, or archived.
     *
     * @return [PNFuture] containing details about created [Channel].
     */
    fun createPublicConversation(
        channelId: String? = null,
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
    ): PNFuture<Channel>

    /**
     * Creates channel for private conversations between two users, letting one person initiate the chat
     * and send an invitation to another person
     *
     * @param invitedUser User that you invite to join a channel.
     * @param channelId ID of the direct channel. The channel ID is created automatically by a hashing function that
     * takes the string of two user names joined by &, computes a numeric value based on the characters in that string,
     * and adds the direct prefix in front. For example, direct.1234567890. You can override this default value by
     * providing your own ID.
     * @param channelName Display name for the channel.
     * If you don't provide the name, the channel will get the same name as id (value of channelId).
     * @param channelDescription Additional details about the channel.
     * @param channelCustom Any custom properties or metadata associated with the channel in the form of a map of
     * key-value pairs.
     * @param channelStatus Current status of the channel, like online, offline, or archived.
     * @param membershipCustom Any custom properties or metadata associated with the user-channel membership in the
     * form of a map of key-value pairs.
     *
     * @return [PNFuture] containing [CreateDirectConversationResult]
     */
    fun createDirectConversation(
        invitedUser: User,
        channelId: String? = null,
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
        membershipCustom: CustomObject? = null,
    ): PNFuture<CreateDirectConversationResult>

    /**
     * Create channel for group communication, promoting collaboration and teamwork.
     * A user can initiate a group chat and invite others to be channel members.
     *
     * @param invitedUsers Users that you invite to join a channel.
     * @param channelId ID of the group channel. The channel ID is created automatically using the v4 UUID generator.
     * You can override it by providing your own ID.
     * @param channelName Display name for the channel. If you don't provide the name, the channel will get the same
     * name as id (value of channelId).
     * @param channelDescription Additional details about the channel.
     * @param channelCustom Any custom properties or metadata associated with the channel in the form of a `Map`.
     * @param channelStatus Current status of the channel, like online, offline, or archived.
     * @param membershipCustom Any custom properties or metadata associated with the membership in the form of a `Map`.
     *
     * @return [PNFuture] containing [CreateGroupConversationResult]
     */
    fun createGroupConversation(
        invitedUsers: Collection<User>,
        channelId: String? = null,
        channelName: String? = null,
        channelDescription: String? = null,
        channelCustom: CustomObject? = null,
        channelStatus: String? = null,
        membershipCustom: CustomObject? = null,
    ): PNFuture<CreateGroupConversationResult>

    /**
     * Lets you watch a selected channel for any new custom events emitted by your chat app.
     *
     * @param channelId Channel to listen for new events.
     * @param customMethod An optional custom method for listening to events, applicable only when [type] is
     * [com.pubnub.chat.types.EventContent.Custom]. If not provided, defaults to [EmitEventMethod.PUBLISH].
     * @param callback A function that is called with an Event<T> as its parameter.
     * It defines the custom behavior to be executed whenever an event is detected on the specified channel.
     *
     * @return AutoCloseable Interface you can call to stop listening for new messages and clean up resources when they
     * are no longer needed by invoking the close() method.
     */
    fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channelId: String,
        customMethod: EmitEventMethod = EmitEventMethod.PUBLISH,
        callback: (event: Event<T>) -> Unit
    ): AutoCloseable

    /**
     * Allows to mute/ban a specific user on a channel or unmute/unban them.
     *
     * Please note that this is a server-side moderation mechanism, as opposed to [Chat.mutedUsersManager] (which is local to
     * a client).
     *
     * @param restriction containing restriction details.
     *
     * @return [PNFuture] that will be completed with Unit.
     */
    fun setRestrictions(
        restriction: Restriction
    ): PNFuture<Unit>

    /**
     * Specifies the channel or channels on which a previously registered device will receive push notifications for new messages.
     *
     * @param channels list of [Channel.id]
     *
     * @return [PNFuture] containing [PNPushAddChannelResult]
     */
    fun registerPushChannels(channels: List<String>): PNFuture<PNPushAddChannelResult>

    /**
     * Specifies the channel or channels on which a registered device will no longer receive push notifications for new messages.
     *
     * @param channels list of [Channel.id]
     *
     * @return [PNFuture] containing [PNPushRemoveChannelResult]
     */
    fun unregisterPushChannels(channels: List<String>): PNFuture<PNPushRemoveChannelResult>

    /**
     * Disable push notifications for a device on all registered channels.
     *
     * @return [PNFuture] containing [PNPushRemoveChannelResult]
     */
    fun unregisterAllPushChannels(): PNFuture<Unit>

    /**
     * Returns info on all messages you didn't read on all joined channels. You can display this number on UI in the
     * channel list of your chat app.
     *
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     * @param filter Expression used to filter the results. Returns only these channels whose properties satisfy the given expression are returned.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc.
     *
     * @return [PNFuture] containing list of [GetUnreadMessagesCounts]
     */
    fun getUnreadMessagesCounts(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<List<GetUnreadMessagesCounts>>

    /**
     * Allows you to mark as read all messages you didn't read on all joined channels.
     *
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     * @param filter Expression used to filter the results. Returns only these channels whose properties satisfy the given expression are returned.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc.
     *
     * @return [PNFuture] containing set of [MarkAllMessageAsReadResponse]
     */
    fun markAllMessagesAsRead(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<MarkAllMessageAsReadResponse>

    /**
     * Retrieves all channels where your registered device receives push notifications.
     *
     * @return [PNFuture] containing list of [Channel.id]
     */
    fun getPushChannels(): PNFuture<List<String>>

    /**
     * Returns historical events that were emitted with the [EmitEventMethod.PUBLISH] method on selected [Channel].
     *
     * @param channelId Channel from which you want to pull historical messages.
     * @param startTimetoken Timetoken delimiting the start of a time slice (exclusive) to pull events from.
     * For details, refer to the History section of PubNub Kotlin SDK
     * @param endTimetoken Timetoken delimiting the end of a time slice (inclusive) to pull events from.
     * For details, refer to the History section of PubNub Kotlin SDK
     * @param count Number of historical events to return for the channel in a single call.
     * You can pull a maximum number of 100 events in a single call. Default is 100.
     *
     * @return [PNFuture] containing [GetEventsHistoryResult]
     */
    fun getEventsHistory(
        channelId: String,
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 100
    ): PNFuture<GetEventsHistoryResult>

    /**
     * Returns all instances when a specific user was mentioned by someone - either in channels or threads.
     * You can use this info to create a channel with all user-related mentions.
     * @param startTimetoken Timetoken delimiting the start of a time slice (exclusive) to pull messages with mentions from.
     * For details, refer to the History section of PubNub Kotlin SDK
     * @param endTimetoken Timetoken delimiting the end of a time slice (inclusive) to pull messages with mentions from.
     * For details, refer to the History section of PubNub Kotlin SDK
     * @param count
     *
     * @return [PNFuture] containing [GetCurrentUserMentionsResult]
     */
    fun getCurrentUserMentions(
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int = 100
    ): PNFuture<GetCurrentUserMentionsResult>

    /**
     * Clears resources of Chat instance and related PubNub SDK instance.
     */
    fun destroy()

    // Companion object required for extending this class elsewhere
    companion object
}

/**
 * Lets you watch a selected channel for any new custom events emitted by your chat app.
 *
 * @param type parameter bounded by the EventContent interface, allowing access to type information at runtime
 * e.g. EventContent.Receipt::class, EventContent.TextMessageContent::class
 * @param channelId Channel to listen for new events.
 * @param customMethod An optional custom method for emitting events. If not provided, defaults to null.
 * @param callback A function that is called with an Event<T> as its parameter.
 * It defines the custom behavior to be executed whenever an event is detected on the specified channel.
 *
 * @return AutoCloseable Interface you can call to stop listening for new messages and clean up resources when they
 * are no longer needed by invoking the close() method.
 */
inline fun <reified T : EventContent> Chat.listenForEvents(
    channelId: String,
    customMethod: EmitEventMethod = EmitEventMethod.PUBLISH,
    noinline callback: (event: Event<T>) -> Unit
): AutoCloseable {
    return listenForEvents(T::class, channelId, customMethod, callback)
}
