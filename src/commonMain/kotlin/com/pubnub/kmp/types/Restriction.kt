package com.pubnub.kmp.types

// todo do we need data class here? Maybe convert to class to save some space?
data class Restriction(val ban: Boolean = false, val mute: Boolean= false, val reason: String? = null)