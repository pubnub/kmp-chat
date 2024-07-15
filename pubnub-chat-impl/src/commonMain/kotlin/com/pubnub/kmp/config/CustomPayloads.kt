package com.pubnub.kmp.config

import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.kmp.types.EventContent

interface CustomPayloads {
    val getMessagePublishBody: ((m: EventContent.TextMessageContent, channelId: String) -> Any)?
    val getMessageResponseBody: ((m: PNMessageResult?, n: PNFetchMessageItem?) -> EventContent.TextMessageContent)?
    val editMessageActionName: String?
    val deleteMessageActionName: String?
}
