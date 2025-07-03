package com.pubnub.integration

import com.pubnub.chat.Message
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelGroupIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun addChannels() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroup = chat.getChannelGroup(randomString())
        channelGroup.addChannels(listOf(channel01, channel02)).await()

        val expectedChannelIds = setOf(channel01.id, channel02.id).toSet()
        val getChannelsResponse = channelGroup.listChannels().await()
        val actualChannelIds = getChannelsResponse.channels.map { it.id }.toSet()

        assertEquals(expectedChannelIds, actualChannelIds)
        chat.pubNub.deleteChannelGroup(channelGroup.id).await()
    }

    @Test
    fun addChannelIdentifiers() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroup = chat.getChannelGroup(randomString())
        channelGroup.addChannelIdentifiers(listOf(channel01.id, channel02.id)).await()

        val expectedChannelIds = setOf(channel01.id, channel02.id).toSet()
        val getChannelsResponse = channelGroup.listChannels().await()
        val actualChannelIds = getChannelsResponse.channels.map { it.id }.toSet()

        assertEquals(expectedChannelIds, actualChannelIds)
        chat.pubNub.deleteChannelGroup(channelGroup.id).await()
    }

    @Test
    fun removeChannels() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroup = chat.getChannelGroup(randomString())
        channelGroup.addChannels(listOf(channel01, channel02)).await()
        channelGroup.removeChannels(listOf(channel01, channel02)).await()

        val getChannelsResponse = channelGroup.listChannels().await()
        assertTrue { getChannelsResponse.channels.isEmpty() }
        chat.pubNub.deleteChannelGroup(channelGroup.id).await()
    }

    @Test
    fun removeChannelIdentifiers() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroup = chat.getChannelGroup(randomString())
        channelGroup.addChannels(listOf(channel01, channel02)).await()
        channelGroup.removeChannelIdentifiers(listOf(channel01.id, channel02.id)).await()

        val getChannelsResponse = channelGroup.listChannels().await()
        assertTrue { getChannelsResponse.channels.isEmpty() }
        chat.pubNub.deleteChannelGroup(channelGroup.id).await()
    }

    @Test
    fun whoIsPresent() = runTest {
        val channelGroup = chat.getChannelGroup(randomString())
        channelGroup.addChannels(listOf(channel01, channel02)).await()
        delayInMillis(1000)

        val autoCloseable = channelGroup.connect { message -> }
        delayInMillis(3000)

        val whoIsPresent = channelGroup.whoIsPresent().await()
        assertEquals(2, whoIsPresent.count())
        assertEquals(whoIsPresent[channel01.id], listOf(chat.currentUser.id))
        assertEquals(whoIsPresent[channel02.id], listOf(chat.currentUser.id))

        chat.pubNub.deleteChannelGroup(channelGroup.id).await()
        autoCloseable?.close()
    }

    @Test
    fun connect() = runTest {
        val channelGroup = chat.getChannelGroup(randomString())
        channelGroup.addChannels(listOf(channel01, channel02)).await()
        delayInMillis(1000)

        val completable = CompletableDeferred<Message>()
        val autoCloseable = channelGroup.connect { message -> completable.complete(message) }

        delayInMillis(2000)
        channel01.sendText("Some message").await()

        val message = completable.await()
        assertEquals("Some message", message.text)
        assertEquals(channel01.id, message.channelId)

        chat.pubNub.deleteChannelGroup(channelGroup.id).await()
        autoCloseable?.close()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun streamPresence() = runTest {
        val channelGroup = chat.getChannelGroup(randomString())
        channelGroup.addChannels(listOf(channel01, channel02)).await()
        val completable = CompletableDeferred<Map<String, List<String>>>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var closeable: AutoCloseable? = null
            pubnub.awaitSubscribe(channelGroups = listOf(channelGroup.id)) {
                closeable = channelGroup.streamPresence {
                    if (it.isNotEmpty()) {
                        completable.complete(it)
                    }
                }
            }

            val closeable2 = channel01Chat02.connect {}
            val completableRes = completable.await()

            assertTrue { completableRes.containsKey(channel01.id) || completableRes.containsKey(channel02.id) }
            assertTrue { completableRes.containsValue(listOf(chat02.currentUser.id)) || completableRes.containsValue(listOf(chat.currentUser.id)) }
            chat.pubNub.deleteChannelGroup(channelGroup.id).await()
            closeable?.close()
            closeable2.close()
        }
    }
}
