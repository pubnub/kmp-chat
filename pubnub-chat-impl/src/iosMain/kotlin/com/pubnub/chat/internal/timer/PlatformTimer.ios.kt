package com.pubnub.chat.internal.timer
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTimer
import kotlin.time.Duration

actual class PlatformTimer(
    private val timer: NSTimer,
    val onCancel: () -> Unit = {}
) {
    actual fun cancel() {
        NSOperationQueue.mainQueue().addOperationWithBlock {
            onCancel()
            timer.invalidate()
        }
    }
}

class TimerManagerImpl : TimerManager {
    private val timersRef: AtomicRef<MutableSet<NSTimer>?> = atomic(mutableSetOf<NSTimer>())

    override fun runPeriodically(period: Duration, action: () -> Unit): PlatformTimer {
        val interval = period.inWholeMilliseconds / 1000.0
        val timer = NSTimer.timerWithTimeInterval(
            interval = interval,
            repeats = true
        ) {
            action()
        }
        NSOperationQueue.mainQueue().addOperationWithBlock {
            timersRef.value?.add(timer) ?: return@addOperationWithBlock
            NSRunLoop.mainRunLoop.addTimer(timer, NSRunLoopCommonModes)
        }
        return PlatformTimer(timer, onCancel = {
            timersRef.value?.remove(timer)
        })
    }

    override fun runWithDelay(delay: Duration, action: () -> Unit): PlatformTimer {
        val interval = delay.inWholeMilliseconds / 1000.0
        val timer = NSTimer.timerWithTimeInterval(
            interval = interval,
            repeats = false
        ) {
            try {
                action()
            } finally {
                timersRef.value?.remove(it)
            }
        }
        NSOperationQueue.mainQueue().addOperationWithBlock {
            timersRef.value?.add(timer) ?: return@addOperationWithBlock
            NSRunLoop.mainRunLoop.addTimer(timer, NSRunLoopCommonModes)
        }
        return PlatformTimer(timer, onCancel = {
            timersRef.value?.remove(timer)
        })
    }

    override fun destroy() {
        val timers = timersRef.getAndSet(null) ?: return
        NSOperationQueue.mainQueue().addOperationWithBlock {
            timers.forEach { it.invalidate() }
        }
    }
}

actual fun createTimerManager(): TimerManager {
    return TimerManagerImpl()
}
