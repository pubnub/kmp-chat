package com.pubnub.kmp.channel

import com.pubnub.kmp.PNFuture

interface TestChannel {
    fun pin(channel: TestChannel): PNFuture<TestChannel>
}

interface TestThreadChannel : TestChannel {
    override fun pin(channel: TestChannel): PNFuture<TestThreadChannel>
}
