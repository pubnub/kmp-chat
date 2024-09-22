package com.pubnub.chat

import com.pubnub.chat.types.EventContent

/**
 * An object that refers to a single piece of information emitted when someone is typing, receiving a message, mentioning others in a message, or reporting a message/user to the admin.
 *
 * Contrary to other Chat SDK entities, this object provides no methods. Its only purpose is to pass payloads of different types emitted when certain chat operations occur.
 */
interface Event<T : EventContent> {
    /**
     * Reference to the main Chat object.
     */
    val chat: Chat

    /**
     * Timetoken of the message that triggered an event.
     */
    val timetoken: Long

    /**
     * Data passed in an event (of [EventContent] subtype) that differ depending on the emitted event type ([EventContent.Typing]], [EventContent.Report], [EventContent.Receipt], [EventContent.Mention], [EventContent.Invite], [EventContent.Custom], [EventContent.Moderation], or [EventContent.TextMessageContent]).
     */
    val payload: T

    /**
     * Target channel where this event is delivered
     */
    val channelId: String

    /**
     * Unique ID of the user that triggered the event.
     */
    val userId: String
}
