package com.pubnub.chat.internal.channel

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteChannelMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetChannelMetadataEventMessage
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.Message
import com.pubnub.chat.internal.DELETED
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.createEventListener
import kotlinx.datetime.Clock

data class ChannelImpl(
    override val chat: Chat,
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
        ::fromDTO,
        com.pubnub.chat.internal.message.MessageImpl::fromDTO
    ) {
    override fun streamUpdates(callback: (channel: Channel) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) {
            callback(it.first())
        }
    }

    companion object {
        fun streamUpdatesOn(
            channels: Collection<Channel>,
            callback: (channels: Collection<Channel>) -> Unit
        ): AutoCloseable {
            if (channels.isEmpty()) {
                throw PubNubException("Cannot stream channel updates on an empty list")
            }
            val chat = channels.first().chat
            val listener = createEventListener(chat.pubNub, onObjects = { pubNub, event ->
                val newChannel = when (val message = event.extractedMessage) {
                    is PNSetChannelMetadataEventMessage -> fromDTO(chat, message.data)
                    is PNDeleteChannelMetadataEventMessage -> ChannelImpl(
                        chat,
                        id = event.channel
                    ) // todo verify behavior with TS Chat SDK
                    else -> return@createEventListener
                }
                val newChannels = channels.map {
                    if (it.id == newChannel.id) {
                        newChannel
                    } else {
                        it
                    }
                }
                callback(newChannels)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(channels.map { it.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        fun fromDTO(chat: Chat, channel: PNChannelMetadata): Channel {
            return ChannelImpl(
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

    override fun copyWithStatusDeleted(): Channel = copy(status = DELETED)
}
