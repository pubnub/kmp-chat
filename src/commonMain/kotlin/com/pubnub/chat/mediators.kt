package com.pubnub.chat

import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.MessageDraft.UserSuggestionSource
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.types.EntityChange
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.then

/**
 * Receive updates when specific messages and related message reactions are updated or removed.
 *
 * Returns the **full collection snapshot** - the entire observed collection is emitted each time
 * any message changes or message reactions are added or removed.
 *
 * @param messages A collection of [Message] objects for which you want to get updates on changed messages or message reactions.
 * @param callback Function that receives the complete collection of monitored messages whenever any one changes.
 * @return Interface that lets you stop receiving message-related updates by invoking the close() method
 */
fun Message.Companion.streamUpdatesOn(
    messages: Collection<Message>,
    callback: (messages: Collection<Message>) -> Unit,
): AutoCloseable = BaseMessage.streamUpdatesOn(messages, callback)

/**
 * Receive updates when specific messages and related message reactions are updated or removed.
 *
 * Provides **individual change notifications** using [EntityChange] instead of full collection snapshots.
 *
 * **Supported events:**
 * - [EntityChange.Updated] - emitted when a message changes or reactions are added or removed
 * **Not supported:**
 * - [EntityChange.Removed] - Messages themselves do not emit removal events
 *
 * @param messages A collection of [Message] objects for which you want to get updates on changed messages or message reactions.
 * @param callback Function that receives an [EntityChange] for each message that changes.
 * @return Interface that lets you stop receiving message-related updates by invoking the close() method
 */
fun Message.Companion.streamChangesOn(
    messages: Collection<Message>,
    callback: (result: Result<EntityChange<Message>>) -> Unit,
): AutoCloseable = BaseMessage.streamChangesOn(messages, callback)

/**
 * Receive updates when specific messages and related message reactions are updated or removed.
 *
 * Returns the **full collection snapshot** - the entire observed collection is emitted each time
 * any message changes or message reactions are added or removed.
 *
 * @param messages A collection of [ThreadMessage] objects for which you want to get updates on changed messages or message reactions.
 * @param callback Function that receives the complete collection of monitored messages whenever any one changes.
 * @return Interface that lets you stop receiving message-related updates by invoking the close() method
 */
fun ThreadMessage.Companion.streamUpdatesOn(
    messages: Collection<ThreadMessage>,
    callback: (messages: Collection<ThreadMessage>) -> Unit,
): AutoCloseable = BaseMessage.streamUpdatesOn(messages, callback)

/**
 * Receive updates when specific messages and related message reactions are updated or removed.
 *
 * Provides **individual change notifications** using [EntityChange] instead of full collection snapshots.
 *
 * **Supported events:**
 * - [EntityChange.Updated] - emitted when a message changes or reactions are added or removed
 * **Not supported:**
 * - [EntityChange.Removed] - Messages themselves do not emit removal events
 *
 * @param messages A collection of [ThreadMessage] objects for which you want to get updates on changed messages or message reactions.
 * @param callback Function that receives an [EntityChange] for each message that changes.
 * @return Interface that lets you stop receiving message-related updates by invoking the close() method
 */
fun ThreadMessage.Companion.streamChangesOn(
    messages: Collection<ThreadMessage>,
    callback: (result: Result<EntityChange<ThreadMessage>>) -> Unit,
): AutoCloseable = BaseMessage.streamChangesOn(messages, callback)

/**
 * Receives updates on a list of [Channel] objects.
 *
 * Returns the **full collection snapshot** - all remaining channels are emitted each time
 * any channel is updated or removed.
 *
 * @param channels Collection of channels to get updates.
 * @param callback Function that receives the complete collection of monitored channels whenever any one changes.
 *
 * @return [AutoCloseable] interface that lets you stop receiving channel-related updates (objects events)
 * and clean up resources by invoking the close() method.
 */
fun Channel.Companion.streamUpdatesOn(
    channels: Collection<Channel>,
    callback: (channels: Collection<Channel>) -> Unit,
): AutoCloseable = BaseChannel.streamUpdatesOn(channels, callback)

/**
 * Receives updates on a list of [Channel] objects.
 *
 * Provides **individual change notifications** using [EntityChange] instead of full collection snapshots.
 *
 * **Supported events:**
 * - [EntityChange.Updated] - emitted when channel metadata changes
 * - [EntityChange.Removed] - emitted when a channel is deleted
 *
 * @param channels Collection of channels to get updates.
 * @param callback Function that receives an [EntityChange] for each channel that changes or is removed.
 *
 * @return [AutoCloseable] interface that lets you stop receiving channel-related updates (objects events)
 * and clean up resources by invoking the close() method.
 */
fun Channel.Companion.streamChangesOn(
    channels: Collection<Channel>,
    callback: (result: Result<EntityChange<Channel>>) -> Unit,
): AutoCloseable = BaseChannel.streamChangesOn(channels, callback)

/**
 * Receives updates on a list of [ThreadChannel] objects.
 *
 * Returns the **full collection snapshot** - all remaining channels are emitted each time
 * any channel is updated or removed.
 *
 * @param channels Collection of channels to get updates.
 * @param callback Function that receives the complete collection of monitored channels whenever any one changes.
 *
 * @return [AutoCloseable] interface that lets you stop receiving channel-related updates (objects events)
 * and clean up resources by invoking the close() method.
 */
fun ThreadChannel.Companion.streamUpdatesOn(
    channels: Collection<Channel>,
    callback: (channels: Collection<Channel>) -> Unit,
): AutoCloseable = BaseChannel.streamUpdatesOn(channels, callback)

/**
 * Receives updates on a list of [ThreadChannel] objects.
 *
 * Provides **individual change notifications** using [EntityChange] instead of full collection snapshots.
 *
 * **Supported events:**
 * - [EntityChange.Updated] - emitted when channel metadata changes
 * - [EntityChange.Removed] - emitted when a channel is deleted
 *
 * @param channels Collection of channels to get updates.
 * @param callback Function that receives an [EntityChange] for each channel that changes or is removed.
 *
 * @return [AutoCloseable] interface that lets you stop receiving channel-related updates (objects events)
 * and clean up resources by invoking the close() method.
 */
fun ThreadChannel.Companion.streamChangesOn(
    channels: Collection<Channel>,
    callback: (result: Result<EntityChange<Channel>>) -> Unit,
): AutoCloseable = BaseChannel.streamChangesOn(channels, callback)

/**
 * Receive updates when specific user-channel Membership object(s) are added, edited, or removed.
 *
 * Returns the **full collection snapshot** - all remaining memberships are emitted each time
 * any membership is updated or removed.
 *
 * @param memberships Collection containing the [Membership]s to watch for updates.
 * @param callback Function that receives the complete collection of monitored memberships whenever any one changes.
 * @return An [AutoCloseable] that you can use to stop receiving objects events by invoking [AutoCloseable.close].
 */
fun Membership.Companion.streamUpdatesOn(
    memberships: Collection<Membership>,
    callback: (memberships: Collection<Membership>) -> Unit,
): AutoCloseable = MembershipImpl.streamUpdatesOn(memberships, callback)

/**
 * Receive updates when specific user-channel Membership object(s) are added, edited, or removed.
 *
 * Provides **individual change notifications** using [EntityChange] instead of full collection snapshots.
 *
 * **Supported events:**
 * - [EntityChange.Updated] - emitted when membership metadata changes
 * - [EntityChange.Removed] - emitted when a membership is deleted
 *
 * **Note:** The id in [EntityChange.Removed] uses the format "channelId:userId" for memberships.
 *
 * @param memberships Collection containing the [Membership]s to watch for updates.
 * @param callback Function that receives an [EntityChange] for each membership that changes or is removed.
 * @return An [AutoCloseable] that you can use to stop receiving objects events by invoking [AutoCloseable.close].
 */
fun Membership.Companion.streamChangesOn(
    memberships: Collection<Membership>,
    callback: (result: Result<EntityChange<Membership>>) -> Unit,
): AutoCloseable = MembershipImpl.streamChangesOn(memberships, callback)

/**
 * Receive updates when specific user objects are updated or removed.
 *
 * Returns the **full collection snapshot** - all remaining users are emitted each time
 * any user is updated or removed.
 *
 * @param users Collection containing the [User]s to watch for updates.
 * @param callback Function that receives the complete collection of monitored users whenever any one changes.
 * @return An [AutoCloseable] that you can use to stop receiving objects events by invoking [AutoCloseable.close].
 */
fun User.Companion.streamUpdatesOn(
    users: Collection<User>,
    callback: (users: Collection<User>) -> Unit
): AutoCloseable = UserImpl.streamUpdatesOn(users, callback)

/**
 * Receive updates when specific user objects are updated or removed.
 *
 * Provides **individual change notifications** using [EntityChange] instead of full collection snapshots.
 *
 * **Supported events:**
 * - [EntityChange.Updated] - emitted when user metadata changes
 * - [EntityChange.Removed] - emitted when a user is deleted
 *
 * @param users Collection containing the [User]s to watch for updates.
 * @param callback Function that receives an [EntityChange] for each user that changes or is removed.
 * @return An [AutoCloseable] that you can use to stop receiving objects events by invoking [AutoCloseable.close].
 */
fun User.Companion.streamChangesOn(
    users: Collection<User>,
    callback: (result: Result<EntityChange<User>>) -> Unit
): AutoCloseable = UserImpl.streamChangesOn(users, callback)

// Message draft v2

/**
 * Creates a [MessageDraft] for composing a message that will be sent to this [Channel].
 *
 * @param userSuggestionSource The scope for searching for suggested users, default: [UserSuggestionSource.CHANNEL]
 * @param isTypingIndicatorTriggered Whether modifying the message text triggers the typing indicator on [Channel], default: true
 * @param userLimit The limit on the number of users returned when searching for users to mention, default: 10
 * @param channelLimit The limit on the number of channels returned when searching for channels to reference, default: 10
 */
fun Channel.createMessageDraft(
    userSuggestionSource: UserSuggestionSource = UserSuggestionSource.CHANNEL,
    isTypingIndicatorTriggered: Boolean = true,
    userLimit: Int = 10,
    channelLimit: Int = 10
): MessageDraft = MessageDraftImpl(this, userSuggestionSource, isTypingIndicatorTriggered, userLimit, channelLimit)

/**
 * Creates a [MessageDraft] for composing a message that will be sent to this [Channel].
 *
 * @param userSuggestionSource The scope for searching for suggested users, default: [UserSuggestionSource.CHANNEL]
 * @param isTypingIndicatorTriggered Whether modifying the message text triggers the typing indicator on [Channel], default: true
 * @param userLimit The limit on the number of users returned when searching for users to mention, default: 10
 * @param channelLimit The limit on the number of channels returned when searching for channels to reference, default: 10
 */
fun Message.createThreadMessageDraft(
    userSuggestionSource: UserSuggestionSource = UserSuggestionSource.CHANNEL,
    isTypingIndicatorTriggered: Boolean = true,
    userLimit: Int = 10,
    channelLimit: Int = 10
): PNFuture<MessageDraft> = createThread().then {
    it.createMessageDraft(userSuggestionSource, isTypingIndicatorTriggered, userLimit, channelLimit)
}

/**
 * Use this on the receiving end if a [Message] was sent using [MessageDraft] to parse the `Message` text into parts
 * representing plain text or additional information such as user mentions, channel references and links.
 */
fun Message.getMessageElements(): List<MessageElement> {
    return MessageDraftImpl.getMessageElements(text)
}

fun QuotedMessage.getMessageElements(): List<MessageElement> {
    return MessageDraftImpl.getMessageElements(text)
}
