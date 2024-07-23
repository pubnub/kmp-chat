package com.pubnub.chat.internal.utils

import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.internal.timer.PlatformTimer
import com.pubnub.kmp.PNFuture
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.concurrent.Volatile
import kotlin.math.pow
import kotlin.time.Duration

class ExponentialRateLimiter(
    private val baseInterval: Duration = Duration.ZERO,
    private val exponentialFactor: Int = 2,
) {
    private var lock = reentrantLock()
    @Volatile
    private var isProcessing: Boolean = false
    @Volatile
    private var currentPenalty: Int = 0
    private val queue: ArrayDeque<Pair<PNFuture<Any>, Consumer<Result<Any>>>> = ArrayDeque()

    fun <T> runWithinLimits(future: PNFuture<T>): PNFuture<T> {
        return if (this.baseInterval == Duration.ZERO) {
            future
        } else {
            PNFuture { completion: Consumer<Result<T>> ->
                lock.withLock {
                    queue.addLast(Pair(future as PNFuture<Any>, completion as Consumer<Result<Any>>))
                    if (!isProcessing) {
                        isProcessing = true
                        PlatformTimer.runWithDelay(Duration.ZERO) {
                            processQueue(0)
                        }
                    }
                }
            }
        }
    }

    private fun processQueue(penalty: Int) {
        lock.withLock {
            val item = queue.removeFirstOrNull()
            if (item == null) {
                isProcessing = false
                currentPenalty = 0
                return
            } else {
                item.first.async {
                    item.second.accept(it)
                }
                PlatformTimer.runWithDelay(this.baseInterval * exponentialFactor.toDouble().pow(penalty)) {
                    processQueue(penalty + 1)
                }
            }
        }
    }
}
