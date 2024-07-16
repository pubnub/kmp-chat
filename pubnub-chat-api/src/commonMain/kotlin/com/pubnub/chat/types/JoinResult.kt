package com.pubnub.chat.types

import com.pubnub.kmp.Membership

class JoinResult(
    val membership: Membership,
    val disconnect: AutoCloseable,
)
