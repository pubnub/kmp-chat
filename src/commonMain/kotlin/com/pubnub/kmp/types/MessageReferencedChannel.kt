package com.pubnub.kmp.types

import kotlinx.serialization.Serializable

@Serializable
data class MessageReferencedChannel(val id: String, val name: String)
