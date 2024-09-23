package com.pubnub.chat.restrictions

/**
 * Represents a restriction applied to a specific user.
 *
 * @property userId The unique identifier of the [com.pubnub.chat.User] who is subject to the restriction.
 * @property channelId The unique identifier of the channel where the restriction applies.
 * @property ban Indicates whether the user is banned from the channel.
 * @property mute Indicates whether the user is muted in the channel.
 * @property reason An optional description or explanation for the restriction.
 */
class Restriction(
    val userId: String,
    val channelId: String,
    val ban: Boolean = false,
    val mute: Boolean = false,
    val reason: String? = null
)
