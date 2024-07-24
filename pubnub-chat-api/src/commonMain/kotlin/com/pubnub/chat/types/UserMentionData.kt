package com.pubnub.chat.types

import com.pubnub.chat.Event
import com.pubnub.chat.Message

class UserMentionData(
    val event: Event<EventContent.Mention>,
    val message: Message?,
    val userId: String,
    val channelId: String? = null,
    val parentChannelId: String? = null,
    val threadChannelId: String? = null
)
