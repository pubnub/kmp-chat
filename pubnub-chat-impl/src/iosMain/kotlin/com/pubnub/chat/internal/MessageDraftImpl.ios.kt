@file:OptIn(ExperimentalForeignApi::class)

package com.pubnub.chat.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSMakeRange
import platform.Foundation.NSRegularExpression
import platform.Foundation.NSTextCheckingResult
import platform.Foundation.matchesInString

private val userMentionRegex = NSRegularExpression(
    pattern = """(?<=^|\p{Space})(@[\p{Alpha}\-]+)""",
    options = 0u,
    error = null
)
private val channelReferenceRegex = NSRegularExpression(
    pattern = """(?<=^|\p{Space})(#[\p{Alpha}\-\d]+)""",
    options = 0u,
    error = null
)

internal actual fun findUserMentionMatches(input: CharSequence): List<RegexMatchResult> {
    return findMatches(input, userMentionRegex)
}

internal actual fun findChannelMentionMatches(input: CharSequence): List<RegexMatchResult> {
    return findMatches(input, channelReferenceRegex)
}

private fun findMatches(input: CharSequence, regex: NSRegularExpression): List<RegexMatchResultImpl> {
    return regex.matchesInString(
        string = input.toString(),
        options = 0u,
        range = NSMakeRange(0uL, input.length.toULong()),
    )
        .filterIsInstance<NSTextCheckingResult>()
        .map {
            it.range().useContents {
                val startOffset = this.location.toInt()
                val endOffset = this.location.toInt() + this.length.toInt()
                RegexMatchResultImpl(
                    input.substring(startOffset, endOffset),
                    startOffset until endOffset
                )
            }
        }
}

internal actual interface RegexMatchResult {
    actual val value: String
    actual val range: IntRange
}

private class RegexMatchResultImpl(override val value: String, override val range: IntRange) : RegexMatchResult
