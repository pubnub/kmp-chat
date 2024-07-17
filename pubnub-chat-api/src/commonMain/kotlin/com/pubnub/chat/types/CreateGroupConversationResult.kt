package com.pubnub.chat.types

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

class CreateGroupConversationResult(
    val channel: Channel,
    val hostMembership: Membership,
    val inviteeMemberships: Array<Membership>
)
