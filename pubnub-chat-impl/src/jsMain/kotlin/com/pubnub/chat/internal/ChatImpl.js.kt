package com.pubnub.chat.internal

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.floor
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi

external val globalThis: dynamic

@OptIn(ExperimentalUuidApi::class)
actual fun generateRandomUuid(): String {
    val uuid = ByteArray(32)
    for (i in 0 until 32) {
        uuid[i] = floor(Random.nextDouble() * 16).toInt().toByte()
    }
    uuid[12] = 4; // set bits 12-15 of time-high-and-version to 0100
    uuid[16] = uuid[19] and (1 shl 2).inv().toByte() // set bit 6 of clock-seq-and-reserved to zero
    uuid[16] = uuid[19] or (1 shl 3).toByte(); // set bit 7 of clock-seq-and-reserved to one
    val uuidString = uuid.joinToString("") { it.toString(16) }
    return uuidString.substring(0, 8) +
        "-" + uuidString.substring(8, 12) +
        "-" + uuidString.substring(12, 16) +
        "-" + uuidString.substring(16, 20) +
        "-" + uuidString.substring(20)
}
