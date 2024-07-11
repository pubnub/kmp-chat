package com.pubnub.kmp.timer

import com.pubnub.kmp.PNFuture
import kotlinx.browser.window
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual class PlatformTimer {
    private var intervalId: Int? = null
    private var timeoutId: Int? = null

    actual companion object {
        actual fun runPeriodically(periodMillis: Duration, action: () -> Unit): PlatformTimer {
            val platformTimer = PlatformTimer()
            platformTimer.intervalId = window.setInterval({
                action()
            }, periodMillis.inWholeMilliseconds.toInt())
            return platformTimer
        }

        actual fun runWithDelay(delayMillis: Duration, action: () -> PNFuture<Unit>): PlatformTimer {
            val platformTimer = PlatformTimer()
            platformTimer.timeoutId = window.setTimeout({
                action()
            }, delayMillis.inWholeMilliseconds.toInt())
            return platformTimer
        }
    }

    actual fun cancel() {
        intervalId?.let { window.clearInterval(it) }
        timeoutId?.let { window.clearTimeout(it) }
        intervalId = null
        timeoutId = null
    }
}
