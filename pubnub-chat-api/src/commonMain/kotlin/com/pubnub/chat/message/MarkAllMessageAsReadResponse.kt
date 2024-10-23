package com.pubnub.chat.message

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.Membership

/**
 * Represents the response returned after marking all messages as read across all joined channels.
 *
 * @property memberships A set of [Membership] objects representing the channels in which the messages have been marked as read.
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 * @property total The total number of channels in which the messages were marked as read.
 * @property status The HTTP status code of the operation, indicating success or failure.
 */
class MarkAllMessageAsReadResponse(
    val memberships: Set<Membership>,
    val next: PNPage.PNNext?,
    val prev: PNPage.PNPrev?,
    val total: Int,
    val status: Int
)
