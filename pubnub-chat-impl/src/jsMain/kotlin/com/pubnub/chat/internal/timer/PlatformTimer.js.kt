package com.pubnub.chat.internal.timer

import kotlin.time.Duration

actual class PlatformTimer(
    private val timerId: Int,
    private val isInterval: Boolean,
    private val onCancel: () -> Unit = {}
) {
    actual fun cancel() {
        onCancel()
        if (isInterval) {
            clearInterval(timerId)
        } else {
            clearTimeout(timerId)
        }
    }
}

class TimerManagerImpl : TimerManager {
    private val timers = mutableMapOf<Int, PlatformTimer>()
    private var counter = 0

    override fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer {
        val count = counter++
        val intervalId = setInterval({
            action()
        }, period.inWholeMilliseconds.toInt())

        return PlatformTimer(intervalId, true, onCancel = {
            timers.remove(count)
        }).also { timer ->
            timers[count] = timer
        }
    }

    override fun runWithDelay(delay: Duration, action: () -> Unit): PlatformTimer {
        val count = counter++
        val timeoutId = setTimeout({
            action()
            timers.remove(count)
        }, delay.inWholeMilliseconds.toInt())
        return PlatformTimer(timeoutId, false, onCancel = {
            timers.remove(count)
        }).also { timer ->
            timers[count] = timer
        }
    }

    override fun destroy() {
        timers.map { it.value }.forEach { it.cancel() }
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

external fun clearInterval(intervalId: Int?)

external fun clearTimeout(intervalId: Int?)

actual fun createTimerManager(): TimerManager {
    return TimerManagerImpl()
}
