package com.pubnub.kmp

import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import kotlinx.datetime.Clock

class ThreadChannel(
    val parentMessage: Message,
    chat: Chat,
    clock: Clock = Clock.System,
    id: String,
    name: String? = null,
    custom: Map<String, Any?>? = null,
    description: String? = null,
    updated: String? = null,
    status: String? = null,
    type: ChannelType? = null
) : Channel(chat, clock, id, name, custom, description, updated, status, type) {

    val parentChannelId: String
        get() = parentMessage.channelId

    companion object {
        fun fromDTO(chat: Chat, parentMessage: Message, channel: PNChannelMetadata): ThreadChannel {
            return ThreadChannel(
                parentMessage,
                chat,
                id = channel.id,
                name = channel.name,
                custom = channel.custom,
                description = channel.description,
                updated = channel.updated,
                status = channel.status,
                type = ChannelType.from(channel.type)
            )
        }
    }
}
