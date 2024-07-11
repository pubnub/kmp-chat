package com.pubnub.kmp.timer

import com.pubnub.kmp.PNFuture
import kotlinx.browser.window

actual class PlatformTimer {
    private var intervalId: Int? = null
    private var timeoutId: Int? = null

    actual fun schedule(periodMillis: Long, action: () -> Unit) {
        intervalId = window.setInterval({
            intervalId = window.setInterval({
                action()
            }, periodMillis.toInt())
        })
    }

    actual fun cancel() {
        intervalId?.let { window.clearInterval(it) }
        timeoutId?.let { window.clearTimeout(it) }
        intervalId = null
        timeoutId = null
    }

    actual fun runWithDelay(delayMillis: Long, action: () -> PNFuture<Unit>) {
        timeoutId = window.setTimeout({
            action()
        }, delayMillis.toInt())
    }
}
