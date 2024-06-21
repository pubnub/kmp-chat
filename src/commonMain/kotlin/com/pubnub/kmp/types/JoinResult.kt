package com.pubnub.kmp.types

import com.pubnub.kmp.membership.Membership

class JoinResult(
    val membership: Membership,
    val disconnect: AutoCloseable,
)
