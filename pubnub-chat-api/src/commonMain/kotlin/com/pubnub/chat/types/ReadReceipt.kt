package com.pubnub.chat.types

/**
 * Represents a read receipt for a single user on a channel.
 *
 * @property userId The ID of the user who read the message.
 * @property lastReadTimetoken The timetoken indicating how far the user has read in the channel.
 */
data class ReadReceipt(
    val userId: String,
    val lastReadTimetoken: Long
)
