package com.pubnub.chat.internal.channel

import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.utils.Clock
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.DELETED
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.ChannelType

data class ChannelImpl(
    override val chat: ChatInternal,
    private val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
) : BaseChannel<Channel, Message>(
        chat = chat,
        clock = clock,
        id = id,
        name = name,
        custom = custom,
        description = description,
        updated = updated,
        status = status,
        type = type,
        channelFactory = ::fromDTO,
        messageFactory = MessageImpl::fromDTO
    ) {
    companion object {
        fun fromDTO(chat: ChatInternal, channel: PNChannelMetadata): Channel {
            return ChannelImpl(
                chat,
                id = channel.id,
                name = channel.name?.value,
                custom = channel.custom?.value,
                description = channel.description?.value,
                updated = channel.updated?.value,
                status = channel.status?.value,
                type = ChannelType.from(channel.type?.value)
            )
        }
    }

    override fun copyWithStatusDeleted(): Channel = copy(status = DELETED)
}
