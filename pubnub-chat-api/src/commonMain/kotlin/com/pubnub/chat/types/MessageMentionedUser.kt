package com.pubnub.chat.types

import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@Serializable
@JsExport
class MessageMentionedUser(val id: String, val name: String)

typealias MessageMentionedUsers = Map<Int, MessageMentionedUser>
