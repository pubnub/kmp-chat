package com.pubnub.chat.types

import com.pubnub.chat.Message

/**
 * Represents the response returned when fetching the historical messages for a [Channel].
 *
 * @param T The type of messages contained in the history response.
 * @property messages A list of messages of type [T] retrieved from the channel history.
 * @property isMore A boolean indicating whether there are more messages available beyond the current result set.
 */
class HistoryResponse<T : Message>(
    val messages: List<T>,
    val isMore: Boolean,
)
