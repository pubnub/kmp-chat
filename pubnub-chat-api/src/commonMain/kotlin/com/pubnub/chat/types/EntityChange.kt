package com.pubnub.chat.types

/**
 * Represents a change to an entity in a stream update.
 *
 * @param T The type of entity being tracked (User, Channel, Message, Membership, etc.)
 */
sealed class EntityChange<out T> {
    /**
     * Indicates that an entity has been created or updated.
     *
     * @property entity The updated entity with its current state
     */
    data class Updated<T>(val entity: T) : EntityChange<T>()

    /**
     * Indicates that an entity has been removed.
     *
     * This event is emitted when the entity has been deleted at the platform level.
     *
     * **Important:** Not all entity types support removal events:
     * - User: Supports removal (via metadata delete events)
     * - Channel: Supports removal (via metadata delete events)
     * - Membership: Supports removal (via metadata delete events)
     * - Message: Does NOT support removal
     * - ThreadMessage: Does NOT support removal
     *
     * @property id The unique identifier of the removed entity
     */
    data class Removed<T>(val id: String) : EntityChange<T>()
}
