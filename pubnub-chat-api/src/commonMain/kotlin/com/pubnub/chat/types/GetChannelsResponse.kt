package com.pubnub.chat.types

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.Channel

/**
 * Represents the response returned after fetching a paginated list of channels.
 *
 * @property channels A list of [Channel] objects representing the channels that were retrieved.
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 * @property total The total number of channels that match the query.
 */
class GetChannelsResponse(
    val channels: List<Channel>,
    val next: PNPage.PNNext?,
    val prev: PNPage.PNPrev?,
    val total: Int
)
