package com.pubnub.chat.internal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

external val globalThis: dynamic

@OptIn(ExperimentalUuidApi::class)
actual fun generateRandomUuid(): String {
    val process = js("process")
    if (process !== undefined && process.versions && process.versions.node && globalThis.crypto === undefined) {
        // Node.js environment detected
        globalThis.crypto = js("require('crypto')")
    }
    return Uuid.random().toString()
}