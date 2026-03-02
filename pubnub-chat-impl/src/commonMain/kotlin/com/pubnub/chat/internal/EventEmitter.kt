package com.pubnub.chat.internal

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.kmp.PNFuture

/**
 * Thin internal wrapper around [PubNub.publish] and [PubNub.signal] that handles
 * kotlinx.serialization encoding and optional payload merging.
 *
 * Separates serialization and transport concerns from business logic so that callers
 * only deal with typed DTOs and never touch wire-format details directly.
 */
internal class EventEmitter(private val pubNub: PubNub) {
    /**
     * Serializes [payload] via [PNDataEncoder], optionally merges additional data (e.g. push
     * notification metadata), and publishes the result to [channel].
     */
    inline fun <reified T> publish(
        channel: String,
        payload: T,
        mergeWith: Map<String, Any>? = null,
        customMessageType: String? = null,
    ): PNFuture<PNPublishResult> {
        val encoded = PNDataEncoder.encode(payload) as Map<String, Any?>
        val message = mergeWith?.let { encoded + it } ?: encoded
        return pubNub.publish(channel = channel, message = message, customMessageType = customMessageType)
    }

    /**
     * Serializes [payload] via [PNDataEncoder] and sends it as a signal to [channel].
     */
    inline fun <reified T> signal(
        channel: String,
        payload: T,
        customMessageType: String? = null,
        mergeWith: Map<String, Any>? = null
    ): PNFuture<PNPublishResult> {
        val encoded = PNDataEncoder.encode(payload) as Map<String, Any?>
        val message = mergeWith?.let { encoded + it } ?: encoded
        return pubNub.signal(channel = channel, message = message, customMessageType = customMessageType)
    }

    /**
     * Publishes a pre-encoded [message] to [channel] without additional serialization.
     */
    fun publish(
        channel: String,
        message: Any,
        customMessageType: String? = null,
    ): PNFuture<PNPublishResult> {
        return pubNub.publish(channel = channel, message = message, customMessageType = customMessageType)
    }
}
