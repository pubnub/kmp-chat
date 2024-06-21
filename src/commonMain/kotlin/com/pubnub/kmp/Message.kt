package com.pubnub.kmp

import com.pubnub.api.decode
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.types.EventContent

data class Message(
    private val chat: Chat,
    val timetoken: Long,
    val content: EventContent.TextMessageContent,
    val channelId: String,
    val userId: String,
    val actions: Map<String, Map<String, List<Action>>>? = null,
    val meta: Map<String, Any>? = null
) {
    val text: String
        get() = content.text // todo implement Message.text() method from TS

    companion object {
        fun fromDTO(chat: Chat, pnMessageResult: PNMessageResult): Message {
            return Message(
                chat,
                pnMessageResult.timetoken!!,
                PNDataEncoder.decode<EventContent>(pnMessageResult.message) as EventContent.TextMessageContent,
                pnMessageResult.channel,
                pnMessageResult.publisher!!,
                meta = pnMessageResult.userMetadata?.decode() as? Map<String, Any>
            )
        }
    }
}
