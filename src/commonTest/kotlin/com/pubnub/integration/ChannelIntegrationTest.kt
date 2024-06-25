package com.pubnub.integration

import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelIntegrationTest: BaseChatIntegrationTest() {

    @Test
    fun join() = runTest {
        val channel = chat.createChannel(randomString()).await()

        val result = channel.join {}.await()

        assertEquals(config.userId.value, result.membership.user.id)
        assertEquals(channel.id, result.membership.channel.id)
    }

    @Test
    fun join_receivesMessages() = runTest {

    }

    @Test
    fun join_close_disconnects() = runTest {

    }

    @Test
    fun join_updates_lastReadMessageTimetoken() = runTest {

    }

    @Test
    fun connect() = runTest {
        
    }

    @Test
    fun connect_receivesMessages() = runTest {

    }

    @Test
    fun connect_close_disconnects() = runTest {

    }




}