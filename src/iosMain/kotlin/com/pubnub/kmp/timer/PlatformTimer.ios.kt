import platform.Foundation.NSTimer
import platform.Foundation.runLoopModeDefault
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_after
import platform.darwin.dispatch_time
import platform.darwin.DISPATCH_TIME_NOW
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.Future

expect class PNFuture<T>

actual class PlatformTimer {
    private val timerRef = AtomicReference<NSTimer?>(null)

    actual fun schedule(periodMillis: Long, action: () -> Unit) {
        val interval = periodMillis / 1000.0
        val actionFrozen = action.freeze()
        val timer = NSTimer.scheduledTimerWithTimeInterval(
            interval = interval,
            repeats = true
        ) {
            actionFrozen()
        }
        timerRef.value = timer
    }

    actual fun cancel() {
        timerRef.value?.invalidate()
        timerRef.value = null
    }

    actual fun runWithDelay(delayMillis: Long, action: () -> PNFuture<Unit>) {
        val interval = delayMillis / 1000.0
        val actionFrozen = action.freeze()
        val timer = NSTimer.scheduledTimerWithTimeInterval(
            interval = interval,
            repeats = false
        ) {
            actionFrozen()
        }
        timerRef.value = timer
        return PNFuture.success(this.freeze())
    }
}