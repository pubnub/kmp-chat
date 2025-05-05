package com.pubnub.chat.message

import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

/**
 * Represents the count of unread messages for a specific user in a given channel.
 *
 * @property channel The [Channel] in which unread messages are being counted.
 * @property membership The [Membership] representing the user's association with the channel.
 * @property next The pagination token for fetching the next page of results, if available.
 * @property prev The pagination token for fetching the previous page of results, if available.
 * @property count The number of unread messages for the user in the specified channel.
 */
class GetUnreadMessagesCounts(
    val channel: Channel,
    val membership: Membership,
    val next: PNPage.PNNext?,
    val prev: PNPage.PNPrev?,
    val count: Long
)
