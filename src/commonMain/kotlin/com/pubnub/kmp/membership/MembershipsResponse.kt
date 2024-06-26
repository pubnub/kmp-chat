package com.pubnub.kmp.membership

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.kmp.Membership

data class MembershipsResponse(
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int,
    val memberships: Set<Membership>
)