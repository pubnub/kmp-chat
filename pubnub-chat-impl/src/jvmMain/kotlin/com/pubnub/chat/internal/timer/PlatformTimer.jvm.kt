package com.pubnub.chat.internal.timer

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

actual class PlatformTimer(
    private val future: ScheduledFuture<*>
) {
    actual fun cancel() {
        future.cancel(true)
    }
}

class TimerManagerImpl(
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : TimerManager {
    override fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer {
        return PlatformTimer(
            executor.scheduleAtFixedRate(action, period.inWholeMilliseconds, period.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        )
    }

    override fun runWithDelay(delay: Duration, action: () -> Unit): PlatformTimer {
        return PlatformTimer(executor.schedule(action, delay.inWholeMilliseconds, TimeUnit.MILLISECONDS))
    }

    override fun destroy() {
        executor.shutdownNow()
    }
}

actual fun createTimerManager(): TimerManager {
    return TimerManagerImpl()
}
