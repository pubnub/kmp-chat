package com.pubnub.chat.user

/**
 * Represents a mention event delivered to a user.
 *
 * @property messageTimetoken The timetoken of the message containing the mention.
 * @property channelId The channel where the mention occurred.
 * @property parentChannelId The parent channel if the mention is in a thread, otherwise null.
 * @property mentionedByUserId The user ID of the message author who created the mention.
 */
class Mention(
    val messageTimetoken: Long,
    val channelId: String,
    val parentChannelId: String?,
    val mentionedByUserId: String,
)
