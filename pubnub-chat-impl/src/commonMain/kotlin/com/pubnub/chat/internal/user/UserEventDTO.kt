package com.pubnub.chat.internal.user

import com.pubnub.api.JsonElement
import com.pubnub.api.models.consumer.pubsub.MessageResult
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.restrictions.RestrictionType
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.createEventListener
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer

/**
 * Internal data transfer objects for deserializing user-targeted event payloads
 */

@Serializable
@SerialName("mention")
internal data class MentionEventData(
    @Serializable(with = LongAsStringSerializer::class) val messageTimetoken: Long,
    val channel: String,
    val parentChannel: String? = null,
)

@Serializable
@SerialName("invite")
internal data class InviteEventData(
    val channelType: ChannelType,
    val channelId: String,
)

@Serializable
@SerialName("moderation")
internal data class ModerationEventData(
    val channelId: String,
    val restriction: RestrictionType,
    val reason: String? = null,
)

/**
 * Subscribes to [channelId], deserializes every incoming published message into [D],
 * and delivers it to [callback] along with the publisher ID and timetoken.
 *
 * Messages that fail to deserialize into [D] are silently dropped.
 *
 * @return [AutoCloseable] that unsubscribes and cleans up when closed.
 */
internal inline fun <reified D> ChatInternal.listenForUserEvent(
    channelId: String,
    crossinline callback: (payload: D, userId: String, timetoken: Long) -> Unit,
): AutoCloseable {
    val listener = createEventListener(pubNub, onMessage = { _, pnEvent ->
        try {
            if (pnEvent.channel != channelId) {
                return@createEventListener
            }
            val message: JsonElement = (pnEvent as? MessageResult)?.message ?: return@createEventListener
            val dto: D = PNDataEncoder.decode(message)
            callback(dto, pnEvent.publisher ?: "", pnEvent.timetoken ?: 0L)
        } catch (_: Exception) {
            // Message does not match the expected DTO shape — skip
        }
    })

    val subscription = pubNub.channel(channelId).subscription()
    subscription.addListener(listener)
    subscription.subscribe()
    return subscription
}
