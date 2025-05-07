package com.pubnub.chat.message

import com.pubnub.api.models.consumer.objects.PNPage

/**
 * Response wrapper for unread message counts that includes pagination information.
 *
 * @property counts List of unread messages for different channels.
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 */
class FetchUnreadMessagesCountsResponse(
    val counts: List<GetUnreadMessagesCounts>,
    val next: PNPage.PNNext?,
    val prev: PNPage.PNPrev?
)
