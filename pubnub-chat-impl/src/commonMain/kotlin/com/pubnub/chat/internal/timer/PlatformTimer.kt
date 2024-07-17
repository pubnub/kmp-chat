package com.pubnub.chat.internal.timer

import com.pubnub.kmp.PNFuture
import kotlin.time.Duration

expect class PlatformTimer {
    companion object {
        fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer

        fun runWithDelay(delay: Duration, action: () -> PNFuture<Unit>): PlatformTimer
    }

    fun cancel()
}
