package com.pubnub.chat.internal.channel

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteChannelMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetChannelMetadataEventMessage
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.DELETED
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.createEventListener
import kotlinx.datetime.Clock

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
        ::fromDTO,
        MessageImpl::fromDTO
    ) {
    override fun streamUpdates(callback: (channel: Channel?) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) {
            callback(it.firstOrNull())
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
            var latestChannels = channels
            val chat = channels.first().chat as ChatInternal
            val listener = createEventListener(chat.pubNub, onObjects = { _, event ->
                val (newChannel, newChannelId) = when (val message = event.extractedMessage) {
                    is PNSetChannelMetadataEventMessage -> fromDTO(chat, message.data) to message.data.id
                    is PNDeleteChannelMetadataEventMessage -> null to message.channel
                    else -> return@createEventListener
                }

                latestChannels = latestChannels.asSequence().filter {
                    it.id != newChannelId
                }.run { newChannel?.let { plus(it) } ?: this }.toList()
                    .also(callback)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(channels.map { it.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        fun fromDTO(chat: ChatInternal, channel: PNChannelMetadata): Channel {
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
