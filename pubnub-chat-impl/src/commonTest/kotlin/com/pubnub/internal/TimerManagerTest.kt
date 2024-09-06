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
    fun destroy_oneOffTask() = runTest {
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
    fun destroy_periodicTask() = runTest {
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

    @Test
    fun cancelSingleTasks() = runTest {
        val timerManager = createTimerManager()
        val counter = atomic(0)
        val counter2 = atomic(0)
        val counter3 = atomic(0)

        // this should run 1 time
        val timer1 = timerManager.runWithDelay(1.seconds) {
            counter.incrementAndGet()
        }
        // this should run 3 times
        // let's also try to start it from a background thread to test if cancellation works in that case
        val timer2 = withContext(Dispatchers.Default) {
            timerManager.runPeriodically(0.5.seconds) {
                counter2.incrementAndGet()
            }
        }
        // this should not run
        val timer3 = timerManager.runWithDelay(2.seconds) {
            counter3.incrementAndGet()
        }

        withContext(Dispatchers.Default) {
            delay(1.75.seconds)
        }
        timer1.cancel()
        timer2.cancel()
        timer3.cancel()

        withContext(Dispatchers.Default) {
            delay(1.seconds)
        }

        assertEquals(1, counter.value)
        assertEquals(3, counter2.value)
        assertEquals(0, counter3.value)
    }
}
