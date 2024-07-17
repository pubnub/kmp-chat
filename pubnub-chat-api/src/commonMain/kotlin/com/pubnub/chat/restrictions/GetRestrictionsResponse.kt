package com.pubnub.chat.restrictions

import com.pubnub.api.models.consumer.objects.PNPage

class GetRestrictionsResponse(
    val restrictions: Set<Restriction>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int
)
