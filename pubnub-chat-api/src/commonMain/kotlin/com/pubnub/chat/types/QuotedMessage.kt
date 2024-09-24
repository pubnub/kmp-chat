package com.pubnub.chat.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer

@Serializable
class QuotedMessage(
    @Serializable(with = LongAsStringSerializer::class) val timetoken: Long,
    val text: String,
    val userId: String
)
