package com.pubnub.chat.types

import com.pubnub.api.models.consumer.objects.PNPage

/**
 * Represents the response returned after fetching read receipts for a channel.
 *
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 * @property total The total number of members that match the query.
 * @property status The HTTP status code of the operation, indicating success or failure.
 * @property receipts A list of [ReadReceipt] objects, one per member who has read a message.
 */
class ReadReceiptsResponse(
    val next: PNPage.PNNext?,
    val prev: PNPage.PNPrev?,
    val total: Int,
    val status: Int,
    val receipts: List<ReadReceipt>
)
