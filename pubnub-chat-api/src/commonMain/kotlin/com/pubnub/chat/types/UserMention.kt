package com.pubnub.chat.types

import com.pubnub.chat.Message

/**
 * Represents a mention of a user in a channel or thread.
 *
 * @property message The [Message] object associated with the mention.
 * @property userId The ID of the user who triggered the mention (the message author).
 * @property channelId The ID of the channel where the mention occurred. For thread mentions, this is the thread channel ID.
 * @property parentChannelId The ID of the parent channel if the mention is in a thread, otherwise null.
 */
class UserMention(
    val message: Message,
    val userId: String,
    val channelId: String,
    val parentChannelId: String?,
)
