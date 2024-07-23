package com.pubnub.chat.internal.timer

import kotlin.time.Duration

expect class PlatformTimer {
    companion object {
        fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer

        fun runWithDelay(delay: Duration, action: () -> Unit): PlatformTimer
    }

    fun cancel()
}
