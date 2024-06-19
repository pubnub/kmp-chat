package com.pubnub.kmp.types

data class Restriction(val ban: Boolean = false, val mute: Boolean= false, val reason: String? = null)