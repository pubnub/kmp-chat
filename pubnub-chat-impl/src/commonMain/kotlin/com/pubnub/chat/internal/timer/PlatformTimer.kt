package com.pubnub.chat.internal.timer

import kotlin.time.Duration

expect fun createTimerManager(): TimerManager

interface TimerManager {
    fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer
    fun runWithDelay(delay: Duration, action: () -> Unit): PlatformTimer
    fun destroy()
}

expect class PlatformTimer {
    fun cancel()
}
