package com.pubnub.chat.user

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.User

class GetUsersResponse(
    val users: List<User>,
    val next: PNPage.PNNext?,
    val prev: PNPage.PNPrev?,
    val total: Int,
)
