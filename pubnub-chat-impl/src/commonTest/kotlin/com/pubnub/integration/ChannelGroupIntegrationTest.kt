package com.pubnub.integration

import com.pubnub.chat.Message
import com.pubnub.chat.internal.channelGroup.ChannelGroupImpl
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelGroupIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun addChannels() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroupId = randomString()
        val channelGroup = ChannelGroupImpl(channelGroupId, chat)

        channelGroup.addChannels(listOf(channel01, channel02)).await()

        val expectedChannelIds = setOf(channel01.id, channel02.id).toSet()
        val getChannelsResponse = channelGroup.listChannels().await()
        val actualChannelIds = getChannelsResponse.channels.map { it.id }.toSet()

        assertEquals(expectedChannelIds, actualChannelIds)
        chat.pubNub.deleteChannelGroup(channelGroupId).await()
    }

    @Test
    fun addChannelIdentifiers() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroupId = randomString()
        val channelGroup = ChannelGroupImpl(channelGroupId, chat)

        channelGroup.addChannelIdentifiers(listOf(channel01.id, channel02.id)).await()

        val expectedChannelIds = setOf(channel01.id, channel02.id).toSet()
        val getChannelsResponse = channelGroup.listChannels().await()
        val actualChannelIds = getChannelsResponse.channels.map { it.id }.toSet()

        assertEquals(expectedChannelIds, actualChannelIds)
        chat.pubNub.deleteChannelGroup(channelGroupId).await()
    }

    @Test
    fun removeChannels() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroupId = randomString()
        val channelGroup = ChannelGroupImpl(channelGroupId, chat)

        channelGroup.addChannels(listOf(channel01, channel02)).await()
        channelGroup.removeChannels(listOf(channel01, channel02)).await()

        val getChannelsResponse = channelGroup.listChannels().await()
        assertTrue { getChannelsResponse.channels.isEmpty() }
        chat.pubNub.deleteChannelGroup(channelGroupId).await()
    }

    @Test
    fun removeChannelIdentifiers() = runTest {
        chat.createChannel(channel01.id).await()
        chat.createChannel(channel02.id).await()

        val channelGroupId = randomString()
        val channelGroup = ChannelGroupImpl(channelGroupId, chat)

        channelGroup.addChannels(listOf(channel01, channel02)).await()
        channelGroup.removeChannelIdentifiers(listOf(channel01.id, channel02.id)).await()

        val getChannelsResponse = channelGroup.listChannels().await()
        assertTrue { getChannelsResponse.channels.isEmpty() }
        chat.pubNub.deleteChannelGroup(channelGroupId).await()
    }

    @Test
    fun whoIsPresent() = runTest {
        val channelGroupId = randomString()
        val channelGroup = ChannelGroupImpl(channelGroupId, chat)

        channelGroup.addChannels(listOf(channel01, channel02)).await()
        delayInMillis(1000)
        val autoCloseable = channelGroup.connect { message -> }
        delayInMillis(3000)

        val whoIsPresent = channelGroup.whoIsPresent().await()
        assertEquals(2, whoIsPresent.count())
        assertEquals(whoIsPresent[channel01.id], listOf(chat.currentUser.id))
        assertEquals(whoIsPresent[channel02.id], listOf(chat.currentUser.id))

        chat.pubNub.deleteChannelGroup(channelGroupId).await()
        autoCloseable?.close()
    }

    @Test
    fun connect() = runTest {
        val channelGroupId = randomString()
        val channelGroup = ChannelGroupImpl(channelGroupId, chat)

        channelGroup.addChannels(listOf(channel01, channel02)).await()
        delayInMillis(1000)

        val completable = CompletableDeferred<Message>()
        val autoCloseable = channelGroup.connect { message -> completable.complete(message) }

        delayInMillis(2000)
        channel01.sendText("Some message").await()

        val message = completable.await()
        assertEquals("Some message", message.text)
        assertEquals(channel01.id, message.channelId)

        chat.pubNub.deleteChannelGroup(channelGroupId).await()
        autoCloseable?.close()
    }

    @Test
    @Ignore
    fun streamPresence() = runTest {
        val channelGroupId = randomString()
        val channelGroup = ChannelGroupImpl(channelGroupId, chat)

        channelGroup.addChannels(listOf(channel01, channel02)).await()
        delayInMillis(2000)

        val completable = CompletableDeferred<Map<String, List<String>>>()
        val connectCloseable = channelGroup.connect { }
        val autoCloseable = channelGroup.streamPresence { completable.complete(it) }

        val whoIsPresent = completable.await()
        assertEquals(2, whoIsPresent.count())
        assertEquals(whoIsPresent[channel01.id], listOf(chat.currentUser.id))
        assertEquals(whoIsPresent[channel02.id], listOf(chat.currentUser.id))

        chat.pubNub.deleteChannelGroup(channelGroupId).await()
        connectCloseable?.close()
        autoCloseable?.close()
    }
}
