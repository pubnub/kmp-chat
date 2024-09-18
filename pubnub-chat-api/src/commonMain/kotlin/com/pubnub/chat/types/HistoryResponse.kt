package com.pubnub.chat.types

import com.pubnub.chat.BaseMessage

class HistoryResponse<T : BaseMessage<T>>(
    val messages: List<T>,
    val isMore: Boolean,
)
