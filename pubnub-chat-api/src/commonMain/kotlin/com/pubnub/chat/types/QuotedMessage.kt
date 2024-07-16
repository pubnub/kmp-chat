package com.pubnub.chat.types

import kotlinx.serialization.Serializable

@Serializable
class QuotedMessage(val timetoken: Long, val text: String, val userId: String)
