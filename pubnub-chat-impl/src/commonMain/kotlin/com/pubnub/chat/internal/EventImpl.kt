package com.pubnub.chat.internal

import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.types.EventContent

class EventImpl<T : EventContent>(
    override val chat: Chat,
    override val timetoken: Long,
    override val payload: T,
    override val channelId: String,
    override val userId: String
) : Event<T> {
    companion object {
        fun fromDTO(
            chat: Chat,
            channelId: String,
            pnFetchMessageItem: PNFetchMessageItem
        ): Event<EventContent> {
            return EventImpl(
                chat = chat,
                timetoken = pnFetchMessageItem.timetoken ?: 0,
                payload = PNDataEncoder.decode<EventContent>(pnFetchMessageItem.message),
                channelId = channelId,
                userId = pnFetchMessageItem.uuid ?: "unknown-user"
            )
        }
    }
}
