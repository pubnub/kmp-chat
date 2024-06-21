package com.pubnub.integration

import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ChannelIntegrationTest: BaseChatIntegrationTest() {

    @Test
    fun join() = runTest {
        val channel = chat.createChannel(randomString()).await()

        val result = channel.join {}.await()

//        assertEquals(config.userId.value, result.membership.user.id)
//        assertEquals(channel.id, result.membership.channel.id)
    }
}