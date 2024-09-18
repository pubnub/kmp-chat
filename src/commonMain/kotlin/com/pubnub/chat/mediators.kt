package com.pubnub.chat

import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.message.BaseMessage

/**
 * Receive updates when specific messages and related message reactions are added, edited, or removed.
 *
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
 * @param callback Function that takes a collection of ThreadMessage objects. It defines the custom behavior to be executed when detecting message or message reaction changes.
 * @return Interface that lets you stop receiving message-related updates by invoking the close() method
 */
fun ThreadMessage.Companion.streamUpdatesOn(
    messages: Collection<ThreadMessage>,
    callback: (messages: Collection<ThreadMessage>) -> Unit,
): AutoCloseable = BaseMessage.streamUpdatesOn(messages, callback)

fun Channel.Companion.streamUpdatesOn(
    channels: Collection<Channel>,
    callback: (channels: Collection<Channel>) -> Unit,
): AutoCloseable = BaseChannel.streamUpdatesOn(channels, callback)

fun ThreadChannel.Companion.streamUpdatesOn(
    channels: Collection<Channel>,
    callback: (channels: Collection<Channel>) -> Unit,
): AutoCloseable = BaseChannel.streamUpdatesOn(channels, callback)

fun Membership.Companion.streamUpdatesOn(
    memberships: Collection<Membership>,
    callback: (memberships: Collection<Membership>) -> Unit,
): AutoCloseable = MembershipImpl.streamUpdatesOn(memberships, callback)

fun User.Companion.streamUpdatesOn(
    users: Collection<User>,
    callback: (users: Collection<User>) -> Unit
): AutoCloseable = UserImpl.streamUpdatesOn(users, callback)
