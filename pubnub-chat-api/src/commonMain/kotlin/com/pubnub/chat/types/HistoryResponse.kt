package com.pubnub.chat.types

import com.pubnub.chat.Message

class HistoryResponse<T : Message>(
    val messages: List<T>,
    val isMore: Boolean,
)
