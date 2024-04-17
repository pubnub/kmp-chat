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
    var pubnubConfig: PNConfiguration? = null
}

class Chat(private val config: ChatConfig) {
    private val pubNub = PubNub(config.pubnubConfig!!)

    fun createUser(
        id: String,
        name: String?,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: Any?  = null,
        status: String? = null,
        type: String? = null,
        callback: (Result<User>) -> Unit,
    ) {
        pubNub.setUUIDMetadata(id, name, externalId, profileUrl, email, custom, includeCustom = true).async {
            result -> callback(result.map { it: PNUUIDMetadataResult ->
                println("got: " + it.data)
                User()
            })
        }
    }
}

@JsExport
class User
