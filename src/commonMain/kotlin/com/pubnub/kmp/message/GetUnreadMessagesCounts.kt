package com.pubnub.kmp.message

import com.pubnub.kmp.Channel
import com.pubnub.kmp.membership.Membership

// todo consider  moving form "data class" to "class"
data class GetUnreadMessagesCounts(
    val channel: Channel,
    val membership: Membership,
    val count: Long
)
