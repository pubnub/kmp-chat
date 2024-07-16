package com.pubnub.chat

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteChannelMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetChannelMetadataEventMessage
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.internal.ChatImpl
import com.pubnub.internal.channel.ChannelImpl
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.PubNub
import com.pubnub.kmp.createEventListener

fun Chat.Companion.init(chatConfiguration: ChatConfiguration, pubnub: PubNub): PNFuture<Chat> {
    return ChatImpl(chatConfiguration, pubnub).initialize()
}

fun Channel.Companion.streamUpdatesOn(
    channels: Collection<Channel>,
    callback: (channels: Collection<Channel>) -> Unit
): AutoCloseable {
    if (channels.isEmpty()) {
        throw PubNubException("Cannot stream channel updates on an empty list")
    }
    val chat = channels.first().chat
    val listener = createEventListener(chat.pubNub, onObjects = { pubNub, event ->
        val newChannel = when (val message = event.extractedMessage) {
            is PNSetChannelMetadataEventMessage -> ChannelImpl.fromDTO(chat, message.data)
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