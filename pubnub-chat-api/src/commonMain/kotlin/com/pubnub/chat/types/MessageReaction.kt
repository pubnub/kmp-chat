package com.pubnub.chat.types

/**
 * Represents a reaction attached to a message.
 *
 * This class provides direct access to all commonly needed reaction information,
 * including the emoji value, whether the current user added the reaction,
 * and the list of user IDs who added it.
 *
 * @property value The emoji or reaction string (e.g., "👍", "❤️", "😂").
 * @property isMine Whether the current user added this reaction.
 * @property userIds List of all user IDs who added this reaction.
 */
data class MessageReaction(
    val value: String,
    val isMine: Boolean,
    val userIds: List<String>
) {
    /**
     * The number of users who added this reaction.
     */
    val count: Int get() = userIds.size
}
