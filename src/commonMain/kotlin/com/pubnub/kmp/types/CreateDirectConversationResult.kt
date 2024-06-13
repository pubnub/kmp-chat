package com.pubnub.kmp.types

import com.pubnub.kmp.Channel
import com.pubnub.kmp.membership.Membership

class CreateDirectConversationResult(
    val channel: Channel,
    val hostMembership: Membership,
    val inviteeMembership: Membership
)
