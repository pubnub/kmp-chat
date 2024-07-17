package com.pubnub.chat.membership

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.Membership

class MembersResponse(
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int,
    val members: Set<Membership> // todo why members is of typ Membership? Shouldn't it be of type PNMember
)
