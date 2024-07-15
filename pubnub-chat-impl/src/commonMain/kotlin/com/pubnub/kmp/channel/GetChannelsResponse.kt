package com.pubnub.kmp.channel

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.kmp.Channel

data class GetChannelsResponse(
    val channels: Set<Channel>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int
)
