package com.pubnub.chat.internal.util

import co.touchlab.kermit.Logger
import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult

// internal fun getPhraseToLookFor(text: String, separator: String): String? {
//    val lastAtIndex = text.lastIndexOf(separator)
//    if (lastAtIndex == -1) {
//        return null
//    }
//    val charactersAfterHash = text.substring(lastAtIndex + 1)
//    if (charactersAfterHash.length < 3) {
//        return null
//    }
//
//    val splitWords: List<String> = charactersAfterHash.split(" ")
//    if (splitWords.size > 2) {
//        return null
//    }
//    return splitWords.joinToString(" ")
// }

internal expect fun urlDecode(encoded: String): String

internal val PNFetchMessagesResult.channelsUrlDecoded: Map<String, List<PNFetchMessageItem>>
    get() = channels.mapKeys {
        urlDecode(
            it.key
        )
    }

inline fun PubNubException.logErrorAndReturnException(log: Logger): PubNubException = this.apply {
    log.e(throwable = this) { this.message.orEmpty() }
}

inline fun PubNubException.logWarnAndReturnException(log: Logger): PubNubException = this.apply {
    log.w(throwable = this) { this.message.orEmpty() }
}

inline fun Logger.pnError(message: String): Nothing = throw PubNubException(message).logErrorAndReturnException(this)

inline fun Logger.logErrorAndReturnException(message: String): PubNubException {
    return PubNubException(message).logErrorAndReturnException(this)
}
