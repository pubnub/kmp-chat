package com.pubnub.chat.internal

import com.pubnub.api.asMap
import com.pubnub.api.asString
import com.pubnub.api.decode
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
            val eventContent: EventContent = try {
                PNDataEncoder.decode<EventContent>(pnFetchMessageItem.message)
            } catch (e: Exception) {
                if (pnFetchMessageItem.message.asMap()?.get(TYPE_OF_MESSAGE)?.asString() == TYPE_OF_MESSAGE_IS_CUSTOM) {
                    EventContent.Custom((pnFetchMessageItem.message.decode() as Map<String, Any?>) - TYPE_OF_MESSAGE)
                } else {
                    throw e
                }
            }
            return EventImpl(
                chat = chat,
                timetoken = pnFetchMessageItem.timetoken ?: 0,
                payload = eventContent,
                channelId = channelId,
                userId = pnFetchMessageItem.uuid ?: "unknown-user"
            )
        }
    }

    override fun toString(): String {
        return "EventImpl(chat=$chat, timetoken=$timetoken, payload=$payload, channelId='$channelId', userId='$userId')"
    }
}
