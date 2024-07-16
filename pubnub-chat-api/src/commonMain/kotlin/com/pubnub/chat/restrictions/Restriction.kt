package com.pubnub.chat.restrictions

class Restriction(
    val userId: String,
    val channelId: String,
    val ban: Boolean = false,
    val mute: Boolean = false,
    val reason: String? = null
)
