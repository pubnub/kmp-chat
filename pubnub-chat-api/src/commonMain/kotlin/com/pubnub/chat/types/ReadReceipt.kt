package com.pubnub.chat.types

/**
 * Represents a read receipt event indicating that a user has read messages up to a specific timetoken.
 *
 * @property userId The identifier of the user who marked messages as read.
 * @property lastReadTimetoken The timetoken of the last message the user has read.
 */
data class ReadReceipt(
    val userId: String,
    val lastReadTimetoken: Long,
)
