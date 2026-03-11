package com.pubnub.integration

import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.ThreadMessage
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import delayForHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        val threadChannel = parentMessage.createThread(threadMessageText).await()
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
        val threadChannel: ThreadChannel = parentMessage.createThread(threadMessageText).await()
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
    fun onThreadMessageUpdated() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val parentMessageText = "Parent message ${randomString()}"
        channel.sendText(parentMessageText).await()
        delayForHistory()
        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel: ThreadChannel = parentMessage.createThread(threadMessageText).await()
        delayForHistory()
        val threadHistory = threadChannel.getHistory().await()
        val threadMessage = threadHistory.messages.first()

        val reactionValue = "like"
        val receivedMessage = CompletableDeferred<ThreadMessage>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var closeable: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(threadChannel.id)) {
                closeable = threadMessage.onThreadMessageUpdated { updatedMessage ->
                    receivedMessage.complete(updatedMessage)
                }
            }

            threadMessage.toggleReaction(reactionValue).await()

            val result = receivedMessage.await()
            assertIs<ThreadMessage>(result)
            assertEquals(true, result.hasUserReaction(reactionValue))

            closeable?.close()
        }

        parentMessage.removeThread().await()
    }
}
