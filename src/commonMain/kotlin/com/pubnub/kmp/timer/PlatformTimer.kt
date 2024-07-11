package com.pubnub.kmp.timer

import com.pubnub.kmp.PNFuture

expect class PlatformTimer() {
    fun schedule(periodMillis: Long, action: () -> Unit)

    fun cancel()

    fun runWithDelay(delayMillis: Long, action: () -> PNFuture<Unit>)
}
