import platform.Foundation.NSTimer
import platform.Foundation.NSRunLoop
import platform.Foundation.runLoopModeDefault
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_after
import platform.darwin.dispatch_time
import platform.darwin.DISPATCH_TIME_NOW
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.native.concurrent.Future

expect class PNFuture<T>

actual class PlatformTimer {
    private val timerRef = AtomicReference<NSTimer?>(null)

    actual companion object {
        @OptIn(ExperimentalTime::class)
        actual fun runPeriodically(periodMillis: Duration, action: () -> Unit): PlatformTimer {
            val platformTimer = PlatformTimer()
            val interval = periodMillis.inWholeMilliseconds / 1000.0
            val actionFrozen = action.freeze()
            val timer = NSTimer.scheduledTimerWithTimeInterval(
                interval = interval,
                repeats = true
            ) {
                actionFrozen()
            }
            platformTimer.timerRef.value = timer
            return platformTimer
        }

        @OptIn(ExperimentalTime::class)
        actual fun runWithDelay(delayMillis: Duration, action: () -> PNFuture<Unit>): PlatformTimer {
            val platformTimer = PlatformTimer()
            val interval = delayMillis.inWholeMilliseconds / 1000.0
            val actionFrozen = action.freeze()
            val timer = NSTimer.scheduledTimerWithTimeInterval(
                interval = interval,
                repeats = false
            ) {
                actionFrozen()
            }
            platformTimer.timerRef.value = timer
            return platformTimer
        }
    }

    actual fun cancel() {
        timerRef.value?.invalidate()
        timerRef.value = null
    }
}