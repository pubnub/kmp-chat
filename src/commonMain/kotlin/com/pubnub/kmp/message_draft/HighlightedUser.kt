package com.pubnub.kmp.message_draft

import com.pubnub.kmp.User

data class HighlightedUser(
    val user: User,
    val nameOccurrenceIndex: Int
)