package com.pubnub.kmp.timer

import com.pubnub.kmp.PNFuture
import java.util.Timer
import java.util.TimerTask

actual class PlatformTimer {
    private var timer: Timer? = null

    actual fun schedule(periodMillis: Long, action: () -> Unit) {
        timer = Timer().apply {
            scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        action()
                    }
                },
                periodMillis, periodMillis
            )
        }
    }

    actual fun cancel() {
        timer?.cancel()
        timer = null
    }

    actual fun runWithDelay(delayMillis: Long, action: () -> PNFuture<Unit>) {
        timer = Timer().apply {
            schedule(
                object : TimerTask() {
                    override fun run() {
                        action()
                    }
                },
                delayMillis
            )
        }
    }
}
