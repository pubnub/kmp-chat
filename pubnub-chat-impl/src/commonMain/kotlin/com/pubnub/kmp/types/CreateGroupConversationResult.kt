package com.pubnub.kmp.types

import com.pubnub.kmp.Channel
import com.pubnub.kmp.Membership

class CreateGroupConversationResult(
    val channel: Channel,
    val hostMembership: Membership,
    val inviteeMemberships: Array<Membership>
)
