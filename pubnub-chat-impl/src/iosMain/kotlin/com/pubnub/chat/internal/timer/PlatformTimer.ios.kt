package com.pubnub.chat.internal.timer
import com.pubnub.kmp.PNFuture
import platform.Foundation.NSTimer
import kotlin.time.Duration

actual class PlatformTimer(
    private val timer: NSTimer
) {
    actual companion object {
        actual fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer {
            val interval = period.inWholeMilliseconds / 1000.0
            val timer = NSTimer.scheduledTimerWithTimeInterval(
                interval = interval,
                repeats = true
            ) {
                action()
            }
            return PlatformTimer(timer)
        }

        actual fun runWithDelay(delay: Duration, action: () -> PNFuture<Unit>): PlatformTimer {
            val interval = delay.inWholeMilliseconds / 1000.0
            val timer = NSTimer.scheduledTimerWithTimeInterval(
                interval = interval,
                repeats = false
            ) {
                action()
            }
            return PlatformTimer(timer)
        }
    }

    actual fun cancel() {
        timer.invalidate()
    }
}