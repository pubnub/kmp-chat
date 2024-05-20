package com.pubnub.kmp.membership

import com.pubnub.api.models.consumer.objects.PNPage

data class MembershipsResponse(
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: String,
    val memberships: List<Membership>
)