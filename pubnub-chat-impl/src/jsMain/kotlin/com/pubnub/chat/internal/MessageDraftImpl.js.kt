package com.pubnub.chat.internal

private val userMentionRegex: Regex = Regex("""(?<=^|\p{space})(@[\p{Alpha}\-\d]+)""")
private val channelReferenceRegex = Regex("""(?<=^|\p{space})(#[\p{Alpha}\-\d]+)""")

internal actual fun findUserMentionMatches(input: CharSequence): List<RegexMatchResult> {
    return userMentionRegex.findAll(input).toList()
}

internal actual fun findChannelMentionMatches(input: CharSequence): List<RegexMatchResult> {
    return channelReferenceRegex.findAll(input).toList()
}

internal actual typealias RegexMatchResult = MatchResult
