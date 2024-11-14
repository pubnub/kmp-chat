@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.chat.types

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@JsExport
class MessageReferencedChannel(val id: String, val name: String)

typealias MessageReferencedChannels = Map<Int, MessageReferencedChannel>
