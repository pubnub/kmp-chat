package com.pubnub.kmp.membership

data class IncludeParameters(
    val totalCount: Boolean = true,
    val customFields: Boolean = true,
    val channelFields: Boolean = true,
    val customChannelFields: Boolean = true
)
