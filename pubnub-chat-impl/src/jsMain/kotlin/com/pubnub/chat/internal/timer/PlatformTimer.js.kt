package com.pubnub.chat.internal.timer

import com.pubnub.kmp.PNFuture
import kotlinx.browser.window
import kotlin.time.Duration

actual class PlatformTimer(
    private val intervalId: Int? = null,
    private val timeoutId: Int? = null
) {
    actual companion object {
        actual fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer {
            val intervalId = window.setInterval({
                action()
            }, period.inWholeMilliseconds.toInt())
            return PlatformTimer(intervalId = intervalId)
        }

        actual fun runWithDelay(delay: Duration, action: () -> PNFuture<Unit>): PlatformTimer {
            val timeoutId = window.setTimeout({
                action()
            }, delay.inWholeMilliseconds.toInt())
            return PlatformTimer(timeoutId = timeoutId)
        }
    }

    actual fun cancel() {
        intervalId?.let { window.clearInterval(it) }
        timeoutId?.let { window.clearTimeout(it) }
    }
}
