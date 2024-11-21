package com.pubnub.kmp.utils

import com.pubnub.kmp.CustomObject

internal actual fun CustomObject.get(key: String): Any? {
    return this[key]
}
