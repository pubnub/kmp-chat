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
            put("type", "custom")
        }
    } else {
        PNDataEncoder.encode(this) as Map<String, Any?>
    }

    if (mergeMessageWith != null) {
        finalMessage = buildMap {
            putAll(finalMessage)
            putAll(mergeMessageWith)
        }
    }
    return finalMessage
}
