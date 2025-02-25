package com.pubnub.chat.types

import com.pubnub.chat.BaseMessage

/**
 * Represents the response returned when fetching the historical messages for a [Channel].
 *
 * @param M The type of messages contained in the history response.
 * @property messages A list of messages of type [M] retrieved from the channel history.
 * @property isMore A boolean indicating whether there are more messages available beyond the current result set.
 */
class HistoryResponse<M : BaseMessage<M, *>>(
    val messages: List<M>,
    val isMore: Boolean,
)
