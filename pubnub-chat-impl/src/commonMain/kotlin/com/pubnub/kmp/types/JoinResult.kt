package com.pubnub.kmp.types

import com.pubnub.kmp.Membership

class JoinResult(
    val membership: Membership,
    val disconnect: AutoCloseable,
)
