package com.pubnub.kmp.utils

import com.pubnub.chat.internal.utils.ExponentialRateLimiter
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.then
import com.pubnub.test.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ExponentialRateLimiterTest {

    private fun PNFuture<Int>.saveTimeElapsed(start: Instant, times: MutableList<Duration>) = this.then {
        println(Clock.System.now() - start)
        times.add(Clock.System.now() - start)
    }

    //todo fix on iOS
    @Test
    fun testDelays() = runTest(timeout = 10.seconds) {
        val start = Clock.System.now()
        val times = mutableListOf<Duration>()
        val future1 = 1.asFuture().saveTimeElapsed(start, times)
        val future2 = 2.asFuture().saveTimeElapsed(start, times)
        val future3 = 3.asFuture().saveTimeElapsed(start, times)
        val future4 = 4.asFuture().saveTimeElapsed(start, times)
        val future5 = 5.asFuture().saveTimeElapsed(start, times)
        val future6 = 6.asFuture().saveTimeElapsed(start, times)

        val expectedTimes = listOf(0, 100, 300, 700, 2800, 2900)

        val rateLimiter = ExponentialRateLimiter(100.milliseconds, 2)

        rateLimiter.runWithinLimits(future1).async {} // 0
        rateLimiter.runWithinLimits(future2).async {} // 100
        rateLimiter.runWithinLimits(future3).async {} // 100 + 200 = 300
        withContext(Dispatchers.Default) { delay(200) } // insert random delay between calls
        rateLimiter.runWithinLimits(future4).await() // 300 + 400 = 700

        // drain old queue and finish processing (penalty resets to 0)
        withContext(Dispatchers.Default) { delay(2000) } // 2000 + 700 + 100 from await()
        rateLimiter.runWithinLimits(future5).async {} // 2800
        rateLimiter.runWithinLimits(future6).await() // 2900

        expectedTimes.forEachIndexed { index, i ->
            //within 80ms accuracy
            assertContains(i..(i + 80), times[index].inWholeMilliseconds.toInt())
        }
    }
}