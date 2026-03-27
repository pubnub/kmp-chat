package com.pubnub.integration

import com.pubnub.chat.Channel
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.PINNED_MESSAGE_TIMETOKEN
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import delayForHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadChannelIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun onThreadMessageReceived() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"
        channel.sendText(parentMessageText).await()
        delayForHistory()
        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val secondThreadMessageText = "Second thread reply ${randomString()}"
        val receivedMessage = CompletableDeferred<ThreadMessage>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var closeable: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(threadChannel.id)) {
                closeable = threadChannel.onThreadMessageReceived { message ->
                    receivedMessage.complete(message)
                }
            }

            threadChannel.sendText(secondThreadMessageText).await()

            val result = receivedMessage.await()
            assertIs<ThreadMessage>(result)
            assertEquals(secondThreadMessageText, result.text)
            assertEquals(channel.id, result.parentChannelId)

            closeable?.close()
        }

        parentMessage.removeThread().await()
    }

    @Test
    fun onThreadChannelUpdated() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"
        channel.sendText(parentMessageText).await()
        delayForHistory()
        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel: ThreadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val expectedDescription = "Updated description ${randomString()}"
        val receivedChannel = CompletableDeferred<ThreadChannel>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var closeable: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(threadChannel.id)) {
                closeable = threadChannel.onThreadChannelUpdated { updatedChannel ->
                    receivedChannel.complete(updatedChannel)
                }
            }

            threadChannel.update(description = expectedDescription).await()

            val result = receivedChannel.await()
            assertIs<ThreadChannel>(result)
            assertEquals(expectedDescription, result.description)
            assertEquals(channel.id, result.parentChannelId)

            closeable?.close()
        }

        parentMessage.removeThread().await()
    }

    @Test
    fun update_returnsThreadChannel() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"

        channel.sendText(parentMessageText).await()
        delayForHistory()

        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val expectedDescription = "Updated description ${randomString()}"
        val updatedThreadChannel = threadChannel.update(description = expectedDescription).await()

        assertIs<ThreadChannel>(updatedThreadChannel)
        assertEquals(expectedDescription, updatedThreadChannel.description)
        assertEquals(channel.id, updatedThreadChannel.parentChannelId)

        parentMessage.removeThread().await()
    }

    @Test
    fun getHistory_returnsThreadMessages() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"

        channel.sendText(parentMessageText).await()
        delayForHistory()

        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel

        delayForHistory()

        val threadHistory = threadChannel.getHistory().await()
        assertTrue(threadHistory.messages.isNotEmpty())

        val message = threadHistory.messages.first()
        assertIs<ThreadMessage>(message)
        assertEquals(threadMessageText, message.text)
        assertEquals(channel.id, message.parentChannelId)

        parentMessage.removeThread().await()
    }

    @Test
    fun getMessage_returnsThreadMessage() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"

        channel.sendText(parentMessageText).await()
        delayForHistory()

        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel

        delayForHistory()

        val threadHistory = threadChannel.getHistory().await()
        val firstThreadMessage = threadHistory.messages.first()

        val retrievedMessage = threadChannel.getMessage(firstThreadMessage.timetoken).await()

        assertNotNull(retrievedMessage)
        assertIs<ThreadMessage>(retrievedMessage)
        assertEquals(threadMessageText, retrievedMessage.text)
        assertEquals(channel.id, retrievedMessage.parentChannelId)

        parentMessage.removeThread().await()
    }

    @Test
    fun pinMessage_andGetPinnedMessage() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"

        channel.sendText(parentMessageText).await()
        delayForHistory()

        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()
        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel

        delayForHistory()

        val threadHistory = threadChannel.getHistory().await()
        val threadMessage = threadHistory.messages.first()
        val updatedThreadChannel = threadChannel.pinMessage(threadMessage).await()

        assertIs<ThreadChannel>(updatedThreadChannel)
        assertEquals(threadMessage.timetoken.toString(), updatedThreadChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN))

        val pinnedMessage = updatedThreadChannel.getPinnedMessage().await()
        assertNotNull(pinnedMessage)
        assertIs<ThreadMessage>(pinnedMessage)
        assertEquals(threadMessage.timetoken, pinnedMessage.timetoken)
        assertEquals(threadMessageText, pinnedMessage.text)

        parentMessage.removeThread().await()
    }

    @Test
    fun unpinMessage() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"

        channel.sendText(parentMessageText).await()
        delayForHistory()

        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel

        delayForHistory()

        val threadHistory = threadChannel.getHistory().await()
        val threadMessage = threadHistory.messages.first()

        val pinnedThreadChannel = threadChannel.pinMessage(threadMessage).await()
        val unpinnedThreadChannel = pinnedThreadChannel.unpinMessage().await()

        assertIs<ThreadChannel>(unpinnedThreadChannel)
        assertNull(unpinnedThreadChannel.getPinnedMessage().await())

        parentMessage.removeThread().await()
    }

    @Test
    fun pinMessageToParentChannel() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"

        channel.sendText(parentMessageText).await()
        delayForHistory()

        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel

        delayForHistory()

        val threadHistory = threadChannel.getHistory().await()
        val threadMessage = threadHistory.messages.first()
        val updatedParentChannel = threadChannel.pinMessageToParentChannel(threadMessage).await()

        assertIs<Channel>(updatedParentChannel)
        assertEquals(threadMessage.timetoken.toString(), updatedParentChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN))

        parentMessage.removeThread().await()
    }

    @Test
    fun unpinMessageFromParentChannel() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"

        channel.sendText(parentMessageText).await()
        delayForHistory()

        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel

        delayForHistory()

        val threadHistory = threadChannel.getHistory().await()
        val threadMessage = threadHistory.messages.first()

        threadChannel.pinMessageToParentChannel(threadMessage).await()
        delayForHistory()

        val updatedParentChannel = threadChannel.unpinMessageFromParentChannel().await()
        assertIs<Channel>(updatedParentChannel)
        assertNull(updatedParentChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN))

        parentMessage.removeThread().await()
    }
}
