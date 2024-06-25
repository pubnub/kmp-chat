package com.pubnub.kmp.restrictions

import com.pubnub.api.models.consumer.objects.PNPage

data class GetRestrictionsResponse(
    val restrictions: Set<Restriction>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int
)