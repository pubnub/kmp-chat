package com.pubnub.chat.internal.timer

import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration

actual class PlatformTimer(
    private val timer: Timer
) {
    actual companion object {
        actual fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer {
            val timer = Timer().apply {
                scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            action()
                        }
                    },
                    period.inWholeMilliseconds,
                    period.inWholeMilliseconds
                )
            }
            return PlatformTimer(timer)
        }

        actual fun runWithDelay(delay: Duration, action: () -> Unit): PlatformTimer {
            val timer = Timer().apply {
                schedule(
                    object : TimerTask() {
                        override fun run() {
                            action()
                        }
                    },
                    delay.inWholeMilliseconds
                )
            }
            return PlatformTimer(timer)
        }
    }

    actual fun cancel() {
        timer.cancel()
    }
}
