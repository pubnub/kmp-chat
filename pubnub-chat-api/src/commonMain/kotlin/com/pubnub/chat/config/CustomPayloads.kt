package com.pubnub.chat.config

import com.pubnub.api.JsonElement
import com.pubnub.chat.types.EventContent

class CustomPayloads(
    val getMessagePublishBody: ((m: EventContent.TextMessageContent, channelId: String) -> Map<String, Any>)? = null,
    val getMessageResponseBody: ((m: JsonElement) -> EventContent.TextMessageContent)? = null,
    val editMessageActionName: String? = null,
    val deleteMessageActionName: String? = null,
)
