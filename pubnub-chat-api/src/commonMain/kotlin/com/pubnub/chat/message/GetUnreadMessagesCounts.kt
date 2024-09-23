package com.pubnub.chat.message

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership

/**
 * Represents the count of unread messages for a specific user in a given channel.
 *
 * @property channel The [Channel] in which unread messages are being counted.
 * @property membership The [Membership] representing the user's association with the channel.
 * @property count The number of unread messages for the user in the specified channel.
 */
class GetUnreadMessagesCounts(
    val channel: Channel,
    val membership: Membership,
    val count: Long
)
