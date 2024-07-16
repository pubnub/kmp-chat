package com.pubnub.chat.user

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.User

data class GetUsersResponse(
    val users: Set<User>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
)
