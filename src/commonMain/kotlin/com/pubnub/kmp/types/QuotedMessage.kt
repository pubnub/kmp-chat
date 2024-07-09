package com.pubnub.kmp.types

import kotlinx.serialization.Serializable

@Serializable
class QuotedMessage(val timetoken: Long, val text: String, val userId: String)
