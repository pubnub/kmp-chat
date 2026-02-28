package com.pubnub.chat.types

data class ReadReceipt(
    val userId: String,
    val lastReadTimetoken: Long,
)
