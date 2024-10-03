package com.pubnub.chat.membership

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.Membership

/**
 * Represents the response returned after fetching user memberships in channels.
 *
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 * @property total The total number of memberships that match the query.
 * @property status The HTTP status code of the operation, indicating success or failure.
 * @property memberships A list of [Membership] objects representing the channels the user is a member of.
 */
class MembershipsResponse(
    val next: PNPage.PNNext?,
    val prev: PNPage.PNPrev?,
    val total: Int,
    val status: Int,
    val memberships: List<Membership>
)
