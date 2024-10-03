package com.pubnub.integration

import com.pubnub.kmp.Uploadable

actual fun generateFileContent(): Uploadable {
    return "some text".byteInputStream()
}