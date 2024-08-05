package com.pubnub.chat.config

import com.pubnub.api.JsonElement
import com.pubnub.chat.types.EventContent

class CustomPayloads(
    val getMessagePublishBody: ((m: EventContent.TextMessageContent, channelId: String) -> Map<String, Any>)? = null,
    val getMessageResponseBody: (
        (m: JsonElement) -> EventContent.TextMessageContent
    )? = null, // todo do we have tests that checks this functionality
    val editMessageActionName: String? = null,
    val deleteMessageActionName: String? = null,
)
