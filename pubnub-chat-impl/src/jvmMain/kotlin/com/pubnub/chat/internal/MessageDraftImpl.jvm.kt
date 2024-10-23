package com.pubnub.chat.internal

private val userMentionRegex: Regex = Regex("""(?U)(?<=^|\p{Space})(@[\p{Alpha}\-]+)""")
private val channelReferenceRegex = Regex("""(?U)(?<=^|\p{Space})(#[\p{Alpha}\-\d]+)""")

internal actual fun findUserMentionMatches(input: CharSequence): List<RegexMatchResult> {
    return userMentionRegex.findAll(input).toList()
}

internal actual fun findChannelMentionMatches(input: CharSequence): List<RegexMatchResult> {
    return channelReferenceRegex.findAll(input).toList()
}

internal actual typealias RegexMatchResult = MatchResult
