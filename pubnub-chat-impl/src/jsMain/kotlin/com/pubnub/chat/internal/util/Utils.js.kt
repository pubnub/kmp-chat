package com.pubnub.chat.internal.util

actual fun urlDecode(encoded: String): String = decodeURIComponent(encoded)

external fun decodeURIComponent(encoded: String): String
