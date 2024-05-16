package com.pubnub.kmp.membership

import com.pubnub.kmp.Channel
import com.pubnub.kmp.User

data class Membership(
    val channel: Channel,
    val user: User,
    val custom: Any?
)