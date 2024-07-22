package com.pubnub.chat.types

import com.pubnub.chat.Membership

class JoinResult(
    val membership: Membership,
    val disconnect: AutoCloseable?,
)
