package com.pubnub.chat.config

import com.pubnub.api.JsonElement
import com.pubnub.chat.types.EventContent

class CustomPayloads(
    /**
     * Function that lets Chat SDK send your custom payload structure.
     *
     * The function will take an [EventContent.TextMessageContent] object and channel id as input,
     * and should produce a `Map` representing the message content, which will be sent as the message payload into PubNub.
     *
     * If you wish to bypass the custom mapping (e.g. for certain channels), you can fall back to the
     * default by calling the third parameter - `defaultMessagePublishBody` and returning its result.
     */
    val getMessagePublishBody: (
        (
            m: EventContent.TextMessageContent,
            channelId: String,
            defaultMessagePublishBody: (m: EventContent.TextMessageContent) -> Map<String, Any?>
        ) -> Map<String, Any?>
    )? = null,
    /**
     * Function that lets Chat SDK receive your custom payload structure.
     * Use it to let Chat SDK translate your custom message payload into the default Chat SDK message format.
     *
     * The function will take an [JsonElement] object and channel id as input,
     * and should produce a [EventContent.TextMessageContent] representing the message content.
     *
     * If you wish to bypass the custom mapping (e.g. for certain channels), you can fall back to the
     * default by calling the third parameter - `defaultMessageResponseBody` and returning its result.
     *
     * Define [getMessagePublishBody] whenever you use [getMessageResponseBody].
     */
    val getMessageResponseBody: (
        (
            m: JsonElement,
            channelId: String,
            defaultMessageResponseBody: (m: JsonElement) -> EventContent.TextMessageContent?
        ) -> EventContent.TextMessageContent?
    )? = null,
    /**
     * A type of action you want to be added to your [Message] object whenever a published message is edited, like "changed" or "modified".
     *
     * The default message action used by Chat SDK is "edited".
     */
    val editMessageActionName: String? = null,
    /**
     * A type of action you want to be added to your [Message] object whenever a published message is deleted, like "removed".
     *
     * The default message action used by Chat SDK is "deleted".
     *
     */
    val deleteMessageActionName: String? = null,
)
