package com.pubnub.chat.internal.util

import platform.Foundation.NSString
import platform.Foundation.stringByRemovingPercentEncoding

internal actual fun urlDecode(encoded: String): String {
    return (encoded as NSString).stringByRemovingPercentEncoding().orEmpty()
}
