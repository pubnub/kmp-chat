package com.pubnub.chat.types

import com.pubnub.chat.Membership

/**
 * Represents the result of a user joining a [Channel] and becoming a member.
 *
 * @property membership The [Membership] object representing the user's membership in the channel.
 * @property disconnect An [AutoCloseable] object that allows the user to stop receiving new messages or updates while retaining channel membership.
 * This can be useful for reducing network traffic or stopping notifications.
 */
class JoinResult(
    val membership: Membership,
    val disconnect: AutoCloseable?,
)
