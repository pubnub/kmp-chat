package com.pubnub.chat.types

/**
 * Represents a report event indicating that a user has reported a message.
 *
 * @property reason The reason for reporting the message.
 * @property text The text of the reported message.
 * @property messageTimetoken The timetoken of the reported message.
 * @property reportedMessageChannelId The channel ID where the reported message was sent.
 * @property reportedUserId The user ID of the user who sent the reported message.
 * @property autoModerationId The auto-moderation ID associated with the report.
 */
data class Report(
    val reason: String,
    val text: String? = null,
    val messageTimetoken: Long? = null,
    val reportedMessageChannelId: String? = null,
    val reportedUserId: String? = null,
    val autoModerationId: String? = null,
)
