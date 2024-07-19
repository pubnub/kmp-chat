package com.pubnub.chat.types

import kotlinx.serialization.Serializable

@Serializable
class MessageReferencedChannel(val id: String, val name: String)

typealias MessageReferencedChannels = Map<Int, MessageReferencedChannel>
