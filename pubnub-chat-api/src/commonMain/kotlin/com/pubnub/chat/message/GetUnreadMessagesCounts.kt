package com.pubnub.chat.message

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

// todo consider  moving form "data class" to "class"
data class GetUnreadMessagesCounts(
    val channel: Channel,
    val membership: Membership,
    val count: Long
)