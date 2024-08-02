package com.pubnub.chat.internal.util

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import org.lighthousegames.logging.KmLog

internal fun getPhraseToLookFor(text: String, separator: String): String? {
    val lastAtIndex = text.lastIndexOf(separator)
    if (lastAtIndex == -1) {
        return null
    }
    val charactersAfterHash = text.substring(lastAtIndex + 1)
    if (charactersAfterHash.length < 3) {
        return null
    }

    val splitWords: List<String> = charactersAfterHash.split(" ")
    if (splitWords.size > 2) {
        return null
    }
    return splitWords.joinToString(" ")
}

internal expect fun urlDecode(encoded: String): String

internal val PNFetchMessagesResult.channelsUrlDecoded: Map<String, List<PNFetchMessageItem>> get() = channels.mapKeys { urlDecode(it.key) }

inline fun <T : Exception> T.logErrorAndReturnException(log: KmLog) = apply {
    log.error(err = this, msg = { this.message.orEmpty() })
}

inline fun pnError(message: String, log: KmLog): Nothing =  throw PubNubException(message).logErrorAndReturnException(log) // todo remove

inline fun KmLog.pnError(message: String): Nothing = throw PubNubException(message).logErrorAndReturnException(this)
