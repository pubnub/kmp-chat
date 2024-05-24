package com.pubnub.kmp.types

import kotlinx.browser.window

class PlatformTimerImpl : PlatformTimer {
    private var timeoutId: Int? = null
    override fun schedule(delayMillis: Long, action: () -> Unit) {
        timeoutId = window.setTimeout({
            action()
        }, delayMillis.toInt())
    }

    override fun cancel() {
        timeoutId?.let {
            window.clearTimeout(it)
        }
    }
}