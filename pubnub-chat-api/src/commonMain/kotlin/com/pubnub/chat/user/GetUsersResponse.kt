package com.pubnub.chat.user

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.kmp.User

data class GetUsersResponse(
    val users: Set<User>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
)
