package com.pubnub.chat.types

import com.pubnub.chat.BaseMessage
import com.pubnub.chat.Event
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadMessage

/**
 * A sealed class representing the data related to a user mention event.
 *
 * @property event The [Event] containing information about the mention event.
 * @property message The [Message] object associated with the mention, if available.
 * @property userId The ID of the user who was mentioned.
 */
sealed class UserMentionData {
    abstract val event: Event<EventContent.Mention>
    abstract val message: BaseMessage<*, *>
    abstract val userId: String
}

/**
 * Represents data related to a mention of a user in a channel.
 *
 * @property channelId The ID of the channel in which the user was mentioned.
 * @property event The [Event] containing information about the mention event.
 * @property message The [Message] object associated with the mention, if available.
 * @property userId The ID of the user who was mentioned.
 */
class ChannelMentionData(
    override val event: Event<EventContent.Mention>,
    override val message: Message,
    override val userId: String,
    val channelId: String
) : UserMentionData()

/**
 * Represents data related to a mention of a user in a thread channel.
 *
 * @property parentChannelId The ID of the parent channel where the thread exists.
 * @property threadChannelId The ID of the thread channel in which the user was mentioned.
 * @property event The [Event] containing information about the mention event.
 * @property message The [Message] object associated with the mention, if available.
 * @property userId The ID of the user who was mentioned.
 */
class ThreadMentionData(
    override val event: Event<EventContent.Mention>,
    override val message: ThreadMessage,
    override val userId: String,
    val parentChannelId: String,
    val threadChannelId: String
) : UserMentionData()
