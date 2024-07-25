package com.pubnub.chat.types

import com.pubnub.chat.Event
import com.pubnub.chat.Message

sealed class UserMentionData {
    abstract val event: Event<EventContent.Mention>
    abstract val message: Message?
    abstract val userId: String
}

// UserMentionDataInChannel
class ChannelMentionData(
    override val event: Event<EventContent.Mention>,
    override val message: Message?,
    override val userId: String,
    val channelId: String
) : UserMentionData()

// UserMentionDataInThreadChannel
class ThreadMentionData(
    override val event: Event<EventContent.Mention>,
    override val message: Message?,
    override val userId: String,
    val parentChannelId: String,
    val threadChannelId: String
) : UserMentionData()
