package com.pubnub.kmp.channel

import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.kmp.Channel
import com.pubnub.kmp.ChatImpl
import com.pubnub.kmp.DELETED
import com.pubnub.kmp.Message
import com.pubnub.kmp.message.MessageImpl
import com.pubnub.kmp.types.ChannelType
import kotlinx.datetime.Clock

data class ChannelImpl(
    override val chat: ChatImpl,
    private val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String,Any?>? = null,
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
    ::fromDTO,
    MessageImpl::fromDTO
) {
    companion object {
        internal fun fromDTO(chat: ChatImpl, channel: PNChannelMetadata): Channel {
            return ChannelImpl(chat,
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

    override fun copyWithStatusDeleted(): Channel = copy(status = DELETED)
}
