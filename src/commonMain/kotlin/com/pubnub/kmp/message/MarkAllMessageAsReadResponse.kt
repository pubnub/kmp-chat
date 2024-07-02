package com.pubnub.kmp.message

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.kmp.Membership

data class MarkAllMessageAsReadResponse(
    val memberships: Set<Membership>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int
)