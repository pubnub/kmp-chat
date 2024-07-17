package com.pubnub.chat.types

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.Channel

class GetChannelsResponse(
    val channels: Set<Channel>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int
)
