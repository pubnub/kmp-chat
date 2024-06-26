package com.pubnub.kmp.membership

import com.pubnub.api.models.consumer.objects.PNPage

data class MembersResponse(
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int,
    val members: Set<Membership>
)