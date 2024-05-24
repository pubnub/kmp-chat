package com.pubnub.kmp.types

interface PlatformTimer {
    fun schedule(delayMillis: Long, action: () -> Unit)
    fun cancel()
}