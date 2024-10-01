package com.pubnub.chat

import com.pubnub.chat.internal.MembershipImpl
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
