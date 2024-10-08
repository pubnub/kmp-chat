package com.pubnub.chat.internal.utils

import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.internal.timer.TimerManager
import com.pubnub.kmp.PNFuture
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.concurrent.Volatile
import kotlin.math.pow
import kotlin.time.Duration

class ExponentialRateLimiter(
    private val baseInterval: Duration = Duration.ZERO,
    private val exponentialFactor: Int = 2,
    private val timerManager: TimerManager
) {
    private val lock = reentrantLock()

    @Volatile
    private var isProcessing: Boolean = false
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
                        timerManager.runWithDelay(Duration.ZERO) {
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
                return
            } else {
                item.first.async {
                    item.second.accept(it)
                }
                timerManager.runWithDelay(this.baseInterval * exponentialFactor.toDouble().pow(penalty)) {
                    processQueue(penalty + 1)
                }
            }
        }
    }
}
