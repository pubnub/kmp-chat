package com.pubnub.chat.types

/**
 * Represents a custom event received on a channel.
 *
 * @property timetoken The timetoken indicating when the event was published.
 * @property userId The ID of the user who emitted the event.
 * @property type The custom message type used to categorize the event.
 * @property payload The custom event payload data.
 */
data class CustomEvent<T>(
    val timetoken: Long,
    val userId: String,
    val type: String?,
    val payload: T
)
