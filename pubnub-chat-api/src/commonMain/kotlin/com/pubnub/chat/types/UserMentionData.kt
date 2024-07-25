package com.pubnub.chat.types

import com.pubnub.chat.Event
import com.pubnub.chat.Message

abstract class UserMentionData {
    abstract val event: Event<EventContent.Mention>
    abstract val message: Message?
    abstract val userId: String
    open val channelId: String? = null
    open val parentChannelId: String? = null
    open val threadChannelId: String? = null
}

// UserMentionDataInChannel
class ChannelMentionData(
    override val event: Event<EventContent.Mention>,
    override val message: Message?,
    override val userId: String,
    override val channelId: String?
) : UserMentionData()

// UserMentionDataInThreadChannel
class ThreadMentionData(
    override val event: Event<EventContent.Mention>,
    override val message: Message?,
    override val userId: String,
    override val parentChannelId: String?,
    override val threadChannelId: String?
) : UserMentionData()
