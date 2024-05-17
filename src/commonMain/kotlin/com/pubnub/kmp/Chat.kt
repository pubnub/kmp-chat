@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class ChannelType {
    var aaa = 0
}

@JsExport
class ChatConfig {
    var uuid: String = ""
    var saveDebugLog: Boolean = false
    var typingTimeout: Int = 0
    var rateLimitPerChannel: Any = mutableMapOf<ChannelType, Int>()
}

@JsExport
class Chat(config: ChatConfig) {
    companion object {
        fun init(config: ChatConfig) = Chat(config)
    }
}