package com.pubnub.chat.types

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

/**
 * Represents the result of creating a direct conversation (private channel) between two users.
 *
 * @property channel The [Channel] object representing the newly created direct conversation.
 * @property hostMembership The [Membership] object representing the channel membership of the user who initiated the conversation.
 * @property inviteeMembership The [Membership] object representing the channel membership of the invited user.
 */
class CreateDirectConversationResult(
    val channel: Channel,
    val hostMembership: Membership,
    val inviteeMembership: Membership
)
