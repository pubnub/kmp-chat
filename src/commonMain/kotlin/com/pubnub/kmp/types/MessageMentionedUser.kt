package com.pubnub.kmp.types

import kotlinx.serialization.Serializable

@Serializable
data class MessageMentionedUser(val id: String, val name: String)

typealias MessageMentionedUsers = Map<Int, MessageMentionedUser>