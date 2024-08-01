package com.pubnub.chat.internal.util

import java.net.URLDecoder

internal actual fun urlDecode(encoded: String): String = URLDecoder.decode(encoded, Charsets.UTF_8.name())