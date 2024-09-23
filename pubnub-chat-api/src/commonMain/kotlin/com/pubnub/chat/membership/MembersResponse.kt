package com.pubnub.chat.membership

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.Membership

/**
 * Represents the response returned after fetching the members of a channel.
 *
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 * @property total The total number of members that match the query.
 * @property status The HTTP status code of the operation, indicating success or failure.
 * @property members A set of [Membership] objects representing the members of the channel.
 */
class MembersResponse(
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int,
    val members: Set<Membership>
)
