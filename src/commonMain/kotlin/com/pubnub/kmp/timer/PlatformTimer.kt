package com.pubnub.kmp.timer

import com.pubnub.kmp.PNFuture
import kotlin.time.Duration

expect class PlatformTimer() {
    companion object {
        fun runPeriodically(periodMillis: Duration, action: () -> Unit): PlatformTimer

        fun runWithDelay(delayMillis: Duration, action: () -> PNFuture<Unit>): PlatformTimer
    }

    fun cancel()
}
