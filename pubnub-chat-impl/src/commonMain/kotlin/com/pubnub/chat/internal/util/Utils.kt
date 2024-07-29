package com.pubnub.chat.internal.util

import com.pubnub.api.models.consumer.history.PNFetchMessagesResult

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

expect fun urlDecode(encoded: String): String

internal val PNFetchMessagesResult.channelsUrlDecoded get() = channels.mapKeys { urlDecode(it.key) }
