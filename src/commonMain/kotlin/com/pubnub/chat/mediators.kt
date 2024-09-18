package com.pubnub.chat

import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.message.BaseMessageImpl

fun Message.Companion.streamUpdatesOn(
    messages: Collection<Message>,
    callback: (messages: Collection<Message>) -> Unit,
): AutoCloseable = BaseMessageImpl.streamUpdatesOn(messages, callback)

fun ThreadMessage.Companion.streamUpdatesOn(
    messages: Collection<ThreadMessage>,
    callback: (messages: Collection<ThreadMessage>) -> Unit,
): AutoCloseable = BaseMessageImpl.streamUpdatesOn(messages, callback)

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
