package com.pubnub.chat.types

/**
 * Enum representing different types of message actions that can be performed on a message.
 *
 * @property REACTIONS Represents a message action related to adding or removing a reaction.
 * @property DELETED Represents a message action related to deleting a message.
 * @property EDITED Represents a message action related to editing a message.
 */
enum class MessageActionType {
    /**
     * Represents a reaction (like, emoji, etc.) added or removed from a message.
     */
    REACTIONS,

    /**
     * Represents a message being deleted.
     */
    DELETED,

    /**
     * Represents a message being edited.
     */
    EDITED;

    /**
     * Converts the enum name to lowercase when represented as a string.
     *
     * @return The enum name in lowercase format.
     */
    override fun toString(): String {
        return name.lowercase()
    }
}
