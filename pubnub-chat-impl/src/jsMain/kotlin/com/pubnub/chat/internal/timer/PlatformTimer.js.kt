package com.pubnub.chat.internal.timer

import kotlinx.browser.window
import kotlin.time.Duration

actual class PlatformTimer(
    private val intervalId: Int? = null,
    private val timeoutId: Int? = null
) {
    actual companion object {
        actual fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer {
            val intervalId = setInterval({
                action()
            }, period.inWholeMilliseconds.toInt())
            return PlatformTimer(intervalId = intervalId)
        }

        actual fun runWithDelay(delay: Duration, action: () -> Unit): PlatformTimer {
            val timeoutId = setTimeout({
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

// because on node js there is no window, we must use these declarations:
external fun setTimeout(
    callback: () -> Unit,
    ms: Int = definedExternally,
): Int

external fun setInterval(
    callback: () -> Unit,
    timeout: Int = definedExternally,
    vararg arguments: Any?
): Int
