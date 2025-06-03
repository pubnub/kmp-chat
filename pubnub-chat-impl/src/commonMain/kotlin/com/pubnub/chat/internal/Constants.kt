package com.pubnub.chat.internal

import co.touchlab.kermit.Logger
import com.pubnub.api.JsonElement
import com.pubnub.api.asString
import com.pubnub.chat.internal.error.PubNubErrorMessage.ERROR_CALLING_DEFAULT_GET_MESSAGE_RESPONSE_BODY
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.types.EventContent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = Logger.withTag("DefaultGetMessage")

internal const val DELETED = "deleted"
internal const val ORIGINAL_PUBLISHER = "originalPublisher"
internal const val ORIGINAL_CHANNEL_ID = "originalChannelId"
internal const val HTTP_ERROR_404 = 404
internal const val HTTP_ERROR_412 = 412
internal const val INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_"
internal const val INTERNAL_USER_MODERATION_CHANNEL_PREFIX = "PUBNUB_INTERNAL_MODERATION."
internal const val PUBNUB_INTERNAL_AUTOMODERATED = "PUBNUB_INTERNAL_AUTOMODERATED"
internal const val INTERNAL_MODERATOR_DATA_ID = "PUBNUB_INTERNAL_MODERATOR"
internal const val INTERNAL_MODERATOR_DATA_TYPE = "internal"
internal const val MESSAGE_THREAD_ID_PREFIX = "PUBNUB_INTERNAL_THREAD"
internal val MINIMAL_TYPING_INDICATOR_TIMEOUT: Duration = 1.seconds
internal const val THREAD_ROOT_ID = "threadRootId"
internal const val PINNED_MESSAGE_TIMETOKEN = "pinnedMessageTimetoken"
internal const val PINNED_MESSAGE_CHANNEL_ID = "pinnedMessageChannelID"
internal const val LAST_ACTIVE_TIMESTAMP = "lastActiveTimestamp"

fun defaultGetMessagePublishBody(m: EventContent.TextMessageContent): Map<String, Any> =
    PNDataEncoder.encode(m as EventContent) as Map<String, Any>

fun defaultGetMessageResponseBody(message: JsonElement): EventContent.TextMessageContent? {
    return message.asString()?.let { messageString -> EventContent.TextMessageContent(messageString, null) }
        ?: try {
            PNDataEncoder.decode<EventContent.TextMessageContent>(message)
        } catch (e: Exception) {
            log.e { "$ERROR_CALLING_DEFAULT_GET_MESSAGE_RESPONSE_BODY ${e.message}" }
            null
        }
}

internal const val METADATA_MENTIONED_USERS = "mentionedUsers"
internal const val METADATA_REFERENCED_CHANNELS = "referencedChannels"
internal const val METADATA_QUOTED_MESSAGE = "quotedMessage"
internal const val METADATA_TEXT_LINKS = "textLinks"
internal const val METADATA_LAST_READ_MESSAGE_TIMETOKEN = "lastReadMessageTimetoken"
internal const val TYPE_OF_MESSAGE = "type"
internal const val TYPE_OF_MESSAGE_IS_CUSTOM = "custom"
internal const val RESTRICTION_BAN = "ban"
internal const val RESTRICTION_MUTE = "mute"
internal const val RESTRICTION_REASON = "reason"

internal const val TYPE_PUBNUB_PRIVATE = "pn.prv"
internal const val PREFIX_PUBNUB_PRIVATE = "PN_PRV."
internal const val SUFFIX_MUTE_1 = "mute1"

internal const val METADATA_AUTO_MODERATION_ID = "pn_mod_id"
