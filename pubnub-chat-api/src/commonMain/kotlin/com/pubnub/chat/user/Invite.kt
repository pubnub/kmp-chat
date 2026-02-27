package com.pubnub.chat.user

import com.pubnub.chat.types.ChannelType

/**
 * Represents an invite event delivered to a user.
 *
 * @property channelId The channel the user is invited to.
 * @property channelType Type of the channel (direct, group, etc.).
 * @property invitedByUserId User ID who sent the invitation.
 * @property invitationTimetoken Timetoken of the invitation.
 */
class Invite(
    val channelId: String,
    val channelType: ChannelType,
    val invitedByUserId: String,
    val invitationTimetoken: Long,
)
