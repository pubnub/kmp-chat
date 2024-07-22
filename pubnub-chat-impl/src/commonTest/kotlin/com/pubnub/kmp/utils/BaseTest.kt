package com.pubnub.kmp.utils

import com.pubnub.api.createJsonElement
import com.pubnub.api.models.consumer.history.HistoryMessageType
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult

open class BaseTest {
    internal fun createPnFetchMessagesResult(
        channelId: String,
        user1: String,
        message1: String,
        timetoken1: Long,
        user2: String,
        message2: String,
        timetoken2: Long
    ) = PNFetchMessagesResult(
        mapOf(
            channelId to listOf(
                PNFetchMessageItem(
                    user1, createJsonElement(mapOf("type" to "text", "text" to message1)), null,
                    timetoken1, null, HistoryMessageType.Message, null
                ),
                PNFetchMessageItem(
                    user2, createJsonElement(mapOf("type" to "text", "text" to message2, "files" to null)), null,
                    timetoken2, null, HistoryMessageType.Message, null
                ),
            )
        ),
        null
    )
}
