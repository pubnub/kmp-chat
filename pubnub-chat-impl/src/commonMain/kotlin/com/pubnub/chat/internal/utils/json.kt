import com.pubnub.chat.internal.TYPE_OF_MESSAGE
import com.pubnub.chat.internal.TYPE_OF_MESSAGE_IS_CUSTOM
import com.pubnub.chat.internal.defaultGetMessagePublishBody
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.types.EventContent

internal fun Any?.tryLong(): Long? {
    return when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }
}

internal fun Any?.tryInt(): Int? {
    return when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }
}

internal fun Any?.tryDouble(): Double? {
    return when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }
}

internal fun EventContent.TextMessageContent.encodeForSending(
    channelId: String,
    getMessagePublishBody: (
        (
            m: EventContent.TextMessageContent,
            channelId: String,
            defaultMessagePublishBody: (m: EventContent.TextMessageContent) -> Map<String, Any?>
        ) -> Map<String, Any?>
    )? = null,
    mergeMessageWith: Map<String, Any>? = null,
): Map<String, Any?> {
    var finalMessage = getMessagePublishBody?.invoke(this, channelId, ::defaultGetMessagePublishBody) ?: defaultGetMessagePublishBody(this)
    if (mergeMessageWith != null) {
        finalMessage = buildMap {
            putAll(finalMessage)
            putAll(mergeMessageWith)
        }
    }
    return finalMessage
}

internal fun EventContent.encodeForSending(
    mergeMessageWith: Map<String, Any>? = null,
): Map<String, Any?> {
    var finalMessage = if (this is EventContent.Custom) {
        buildMap<String, Any?> {
            putAll(data)
            put(TYPE_OF_MESSAGE, TYPE_OF_MESSAGE_IS_CUSTOM)
        }
    } else {
        encodeEventContent(this)
    }

    if (mergeMessageWith != null) {
        finalMessage = buildMap {
            putAll(finalMessage)
            putAll(mergeMessageWith)
        }
    }
    return finalMessage
}

private fun encodeEventContent(content: EventContent): Map<String, Any?> {
    return when (content) {
        is EventContent.Typing ->
            PNDataEncoder.encode(EventContent.Typing.serializer(), content) as Map<String, Any?>
        is EventContent.Report ->
            PNDataEncoder.encode(EventContent.Report.serializer(), content) as Map<String, Any?>
        is EventContent.Receipt ->
            PNDataEncoder.encode(EventContent.Receipt.serializer(), content) as Map<String, Any?>
        is EventContent.Mention ->
            PNDataEncoder.encode(EventContent.Mention.serializer(), content) as Map<String, Any?>
        is EventContent.Invite ->
            PNDataEncoder.encode(EventContent.Invite.serializer(), content) as Map<String, Any?>
        is EventContent.Moderation ->
            PNDataEncoder.encode(EventContent.Moderation.serializer(), content) as Map<String, Any?>
        is EventContent.UnknownMessageFormat ->
            PNDataEncoder.encode(EventContent.TextMessageContent.serializer(), content) as Map<String, Any?>
        is EventContent.TextMessageContent ->
            PNDataEncoder.encode(EventContent.TextMessageContent.serializer(), content) as Map<String, Any?>
        is EventContent.Custom ->
            error("Custom event content should be handled before generic serialization.")
        else ->
            error("Unsupported EventContent subtype for serialization: ${content::class}")
    }
}
