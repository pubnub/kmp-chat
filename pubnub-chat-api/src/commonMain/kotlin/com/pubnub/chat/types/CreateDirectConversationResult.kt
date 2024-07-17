package com.pubnub.chat.types

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

class CreateDirectConversationResult(
    val channel: Channel,
    val hostMembership: Membership,
    val inviteeMembership: Membership
)
