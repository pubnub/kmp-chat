package com.pubnub.chat.types

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

/**
 * Represents the result of creating a group conversation (group channel) for collaborative communication.
 *
 * @property channel The [Channel] object representing the newly created group conversation.
 * @property hostMembership The [Membership] object representing the channel membership of the user who initiated
 * the group conversation.
 * @property inviteeMemberships An array of [Membership] objects representing the channel memberships of the users
 * invited to the group conversation.
 */
class CreateGroupConversationResult(
    val channel: Channel,
    val hostMembership: Membership,
    val inviteeMemberships: Array<Membership>
)
