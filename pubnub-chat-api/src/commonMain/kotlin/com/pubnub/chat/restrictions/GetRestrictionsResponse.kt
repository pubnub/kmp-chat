package com.pubnub.chat.restrictions

import com.pubnub.api.models.consumer.objects.PNPage

/**
 * Stores information about users' restriction on a given [com.pubnub.chat.Channel]
 *
 * @property restrictions A set of [Restriction] objects that represent the restrictions applied to the user across different channels.
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 * @property total The total number of restrictions returned in the current result set.
 * @property status The HTTP status code of the operation, indicating success or failure.
 */
class GetRestrictionsResponse(
    val restrictions: Set<Restriction>,
    val next: PNPage?,
    val prev: PNPage?,
    val total: Int,
    val status: Int
)
