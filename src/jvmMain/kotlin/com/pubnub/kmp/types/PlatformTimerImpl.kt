package com.pubnub.kmp.types

import java.util.*

class PlatformTimerImpl : PlatformTimer{
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null

    override fun schedule(delayMillis: Long, action: () -> Unit) {
        cancel() // Cancel any existing timer
        timer = Timer().apply {
            timerTask = object : TimerTask() {
                override fun run() {
                    action()
                }
            }
            schedule(timerTask, delayMillis)
        }
    }

    override fun cancel() {
        timerTask?.cancel()
        timer?.cancel()
        timer = null
        timerTask = null
    }
}