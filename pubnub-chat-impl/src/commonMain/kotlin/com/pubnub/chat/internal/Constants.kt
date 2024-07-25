package com.pubnub.chat.internal

import com.pubnub.api.JsonElement
import com.pubnub.api.asString
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.types.EventContent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val DELETED = "Deleted"
internal const val ID_IS_REQUIRED = "Id is required"
internal const val CHANNEL_ID_IS_REQUIRED = "Channel Id is required"
internal const val ORIGINAL_PUBLISHER = "originalPublisher"
internal const val HTTP_ERROR_404 = 404
internal const val INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_"
internal const val MESSAGE_THREAD_ID_PREFIX = "PUBNUB_INTERNAL_THREAD"
internal val MINIMAL_TYPING_INDICATOR_TIMEOUT: Duration = 1.seconds
internal const val THREAD_ROOT_ID = "threadRootId"
internal const val INTERNAL_ADMIN_CHANNEL = "PUBNUB_INTERNAL_ADMIN_CHANNEL"

fun defaultGetMessagePublishBody(m: EventContent.TextMessageContent, channelId: String): Map<String, Any> =
    PNDataEncoder.encode(m as EventContent) as Map<String, Any>

fun defaultGetMessageResponseBody(message: JsonElement): EventContent.TextMessageContent? {
    return message.asString()?.let { messageString -> EventContent.TextMessageContent(messageString, null) }
        ?: try {
            PNDataEncoder.decode<EventContent.TextMessageContent>(message)
        } catch (e: Exception) {
            null
        } // todo log?
}

internal const val METADATA_MENTIONED_USERS = "mentionedUsers"
internal const val METADATA_REFERENCED_CHANNELS = "referencedChannels"
internal const val METADATA_QUOTED_MESSAGE = "quotedMessage"
internal const val METADATA_TEXT_LINKS = "textLinks"
internal const val METADATA_LAST_READ_MESSAGE_TIMETOKEN = "lastReadMessageTimetoken"
