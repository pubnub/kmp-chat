package com.pubnub.internal

import com.pubnub.chat.internal.timer.createTimerManager
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class TimerManagerTest {
    @Test
    fun cancelOneOff() = runTest {
        val timerManager = createTimerManager()
        val completable1 = CompletableDeferred<Unit>()
        val completable2 = CompletableDeferred<Unit>()

        // this should run
        timerManager.runWithDelay(1.seconds) {
            completable1.complete(Unit)
        }

        // this shouldn't run
        timerManager.runWithDelay(2.seconds) {
            completable2.complete(Unit)
        }

        completable1.await()
        timerManager.destroy()

        // give completable2 a chance to complete (in case destroy() doesn't work)
        withContext(Dispatchers.Default) {
            delay(2.seconds)
        }

        assertFalse { completable2.isCompleted }
    }

    @Test
    fun cancelPeriodic() = runTest {
        val timerManager = createTimerManager()
        val counter = atomic(0)
        val counter2 = atomic(0)

        // this should run 1 time
        timerManager.runPeriodically(1.seconds) {
            counter.incrementAndGet()
        }

        // this should run 3 times
        // let's also try to start it from a background thread to test if cancellation works in that case
        withContext(Dispatchers.Default) {
            timerManager.runPeriodically(0.5.seconds) {
                counter2.incrementAndGet()
            }
        }

        withContext(Dispatchers.Default) {
            delay(1.75.seconds)
        }
        timerManager.destroy()

        withContext(Dispatchers.Default) {
            delay(1.seconds)
        }

        assertEquals(1, counter.value)
        assertEquals(3, counter2.value)
    }
}
