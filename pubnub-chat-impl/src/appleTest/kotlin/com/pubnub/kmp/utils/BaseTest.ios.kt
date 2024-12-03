package com.pubnub.kmp.utils

import com.pubnub.kmp.CustomObject

internal actual fun CustomObject.get(key: String): Any? {
    return (value as Map<String, Any>)[key]
}
