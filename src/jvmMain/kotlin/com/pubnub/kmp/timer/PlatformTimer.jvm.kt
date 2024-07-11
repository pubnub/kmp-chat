package com.pubnub.kmp.timer

import com.pubnub.kmp.PNFuture
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration

actual class PlatformTimer {
    private var timer: Timer? = null

    actual companion object {
        actual fun runPeriodically(periodMillis: Duration, action: () -> Unit): PlatformTimer {
            val platformTimer = PlatformTimer()
            platformTimer.timer = Timer().apply {
                scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            action()
                        }
                    },
                    periodMillis.inWholeMilliseconds, periodMillis.inWholeMilliseconds
                )
            }
            return platformTimer
        }

        actual fun runWithDelay(delayMillis: Duration, action: () -> PNFuture<Unit>): PlatformTimer {
            val platformTimer = PlatformTimer()
            platformTimer.timer = Timer().apply {
                schedule(
                    object : TimerTask() {
                        override fun run() {
                            action()
                        }
                    },
                    delayMillis.inWholeMilliseconds
                )
            }
            return platformTimer
        }
    }

    actual fun cancel() {
        timer?.cancel()
        timer = null
    }
}
