package com.pubnub.internal.utils

internal fun cyrb53a(str: String, seed: Int = 0): ULong {
    var h1 = 0xdeadbeefL.toInt() xor seed
    var h2 = 0x41c6ce57L.toInt() xor seed
    str.map { it.code }.forEach { ch ->
        h1 = (h1 xor ch) * 0x85ebca77L.toInt()
        h2 = (h2 xor ch) * 0xc2b2ae3dL.toInt()
    }

    h1 = h1 xor ((h1 xor (h2 ushr 15)) * 0x735a2d97L.toInt())
    h2 = h2 xor ((h2 xor (h1 ushr 15)) * 0xcaf649a9L.toInt())
    h1 = h1 xor (h2 ushr 16)
    h2 = h2 xor (h1 ushr 16)
    val result = (2097152L * h2 + (h1 ushr 11))
    if (result < 0) {
        return result.toULong() and (("111111111111".toULong(2) shl 53).inv())
    } else {
        return result.toULong()
    }
}
