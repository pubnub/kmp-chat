package com.pubnub.chat

import com.pubnub.chat.MessageDraft.UserSuggestionSource
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.message.BaseMessage

/**
 * Receive updates when specific messages and related message reactions are added, edited, or removed.
 *
 * @param messages A collection of [Message] objects for which you want to get updates on changed messages or message reactions.
 * @param callback Function that takes a collection of Message objects. It defines the custom behavior to be executed when detecting message or message reaction changes.
 * @return Interface that lets you stop receiving message-related updates by invoking the close() method
 */
fun Message.Companion.streamUpdatesOn(
    messages: Collection<Message>,
    callback: (messages: Collection<Message>) -> Unit,
): AutoCloseable = BaseMessage.streamUpdatesOn(messages, callback)

/**
 * Receive updates when specific messages and related message reactions are added, edited, or removed.
 *
 * @param messages A collection of [ThreadMessage] objects for which you want to get updates on changed messages or message reactions.
 * @param callback Function that takes a collection of ThreadMessage objects. It defines the custom behavior to be executed when detecting message or message reaction changes.
 * @return Interface that lets you stop receiving message-related updates by invoking the close() method
 */
fun ThreadMessage.Companion.streamUpdatesOn(
    messages: Collection<ThreadMessage>,
    callback: (messages: Collection<ThreadMessage>) -> Unit,
): AutoCloseable = BaseMessage.streamUpdatesOn(messages, callback)

/**
 * Receives updates on list of [Channel] object.
 *
 * @param channels Collection of channels to get updates.
 * @param callback Function that takes a single Channel object. It defines the custom behavior to be executed when
 * detecting channel changes.
 *
 * @return [AutoCloseable] interface that lets you stop receiving channel-related updates (objects events)
 * and clean up resources by invoking the close() method.
 */
fun Channel.Companion.streamUpdatesOn(
    channels: Collection<Channel>,
    callback: (channels: Collection<Channel>) -> Unit,
): AutoCloseable = BaseChannel.streamUpdatesOn(channels, callback)

/**
 * Receives updates on list of [Channel] object.
 *
 * @param channels Collection of channels to get updates.
 * @param callback Function that takes a single Channel object. It defines the custom behavior to be executed when
 * detecting channel changes.
 *
 * @return [AutoCloseable] interface that lets you stop receiving channel-related updates (objects events)
 * and clean up resources by invoking the close() method.
 */
fun ThreadChannel.Companion.streamUpdatesOn(
    channels: Collection<Channel>,
    callback: (channels: Collection<Channel>) -> Unit,
): AutoCloseable = BaseChannel.streamUpdatesOn(channels, callback)

/**
 * You can receive updates when specific user-channel Membership object(s) are added, edited, or removed.
 *
 * @param memberships Collection containing the [Membership]s to watch for updates.
 * @param callback Defines the custom behavior to be executed when detecting membership changes.
 * @return An [AutoCloseable] that you can use to stop receiving objects events by invoking [AutoCloseable.close].
 */
fun Membership.Companion.streamUpdatesOn(
    memberships: Collection<Membership>,
    callback: (memberships: Collection<Membership>) -> Unit,
): AutoCloseable = MembershipImpl.streamUpdatesOn(memberships, callback)

fun User.Companion.streamUpdatesOn(
    users: Collection<User>,
    callback: (users: Collection<User>) -> Unit
): AutoCloseable = UserImpl.streamUpdatesOn(users, callback)

// message draft v2

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
 * Use this on the receiving end if a [Message] was sent using [MessageDraft] to parse the `Message` text into parts
 * representing plain text or additional information such as user mentions, channel references and links.
 */
fun Message.getMessageElements(): List<MessageElement> {
    return MessageDraftImpl.getMessageElements(text)
}
