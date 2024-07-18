package com.pubnub.chat.types

import kotlinx.serialization.Serializable

@Serializable
class MessageMentionedUser(val id: String, val name: String)

typealias MessageMentionedUsers = Map<Int, MessageMentionedUser>
