package com.pubnub.chat.message

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

class GetUnreadMessagesCounts(
    val channel: Channel,
    val membership: Membership,
    val count: Long
)
