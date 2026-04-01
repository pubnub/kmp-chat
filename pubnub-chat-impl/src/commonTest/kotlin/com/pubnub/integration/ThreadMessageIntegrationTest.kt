package com.pubnub.integration

import com.pubnub.chat.Channel
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadMessageIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun editText_returnsThreadMessage() = runTest {
        val channel = chat.createChannel(randomString()).await()
        channel.sendText("Parent message ${randomString()}").await()
        delayForHistory()

        val parentMessage = channel.getHistory().await().messages.first()
        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val threadMessage = threadChannel.getHistory().await().messages.first()
        val newText = "Edited thread reply ${randomString()}"
        val editedMessage = threadMessage.editText(newText).await()

        assertIs<ThreadMessage>(editedMessage)
        assertEquals(newText, editedMessage.text)
        assertEquals(channel.id, editedMessage.parentChannelId)
        assertEquals(threadMessage.timetoken, editedMessage.timetoken)

        parentMessage.removeThread().await()
    }

    @Test
    fun toggleReaction_returnsThreadMessage() = runTest {
        val channel = chat.createChannel(randomString()).await()
        channel.sendText("Parent message ${randomString()}").await()
        delayForHistory()

        val parentMessage = channel.getHistory().await().messages.first()
        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val threadMessage = threadChannel.getHistory().await().messages.first()
        val reaction = "thumbsup"
        val messageWithReaction = threadMessage.toggleReaction(reaction).await()

        assertIs<ThreadMessage>(messageWithReaction)
        assertTrue(messageWithReaction.hasUserReaction(reaction))
        assertEquals(channel.id, messageWithReaction.parentChannelId)
        assertEquals(threadMessage.timetoken, messageWithReaction.timetoken)

        val messageWithoutReaction = messageWithReaction.toggleReaction(reaction).await()

        assertIs<ThreadMessage>(messageWithoutReaction)
        assertFalse(messageWithoutReaction.hasUserReaction(reaction))
        assertEquals(channel.id, messageWithoutReaction.parentChannelId)

        parentMessage.removeThread().await()
    }

    @Test
    fun restore_returnsThreadMessage() = runTest {
        val channel = chat.createChannel(randomString()).await()
        channel.sendText("Parent message ${randomString()}").await()
        delayForHistory()

        val parentMessage = channel.getHistory().await().messages.first()
        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val threadMessage = threadChannel.getHistory().await().messages.first()
        val deletedMessage = threadMessage.delete(soft = true).await()
        assertNotNull(deletedMessage)
        assertTrue(deletedMessage.deleted)

        val restoredMessage = deletedMessage.restore().await()

        assertIs<ThreadMessage>(restoredMessage)
        assertEquals(threadMessageText, restoredMessage.text)
        assertEquals(channel.id, restoredMessage.parentChannelId)
        assertEquals(threadMessage.timetoken, restoredMessage.timetoken)

        parentMessage.removeThread().await()
    }

    @Test
    fun pinToParentChannel() = runTest {
        val channel = chat.createChannel(randomString()).await()
        channel.sendText("Parent message ${randomString()}").await()
        delayForHistory()

        val parentMessage = channel.getHistory().await().messages.first()
        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val threadMessage = threadChannel.getHistory().await().messages.first()
        val updatedParentChannel = threadMessage.pinToParentChannel().await()

        assertIs<ThreadMessage>(threadMessage)
        assertIs<Channel>(updatedParentChannel)
        assertEquals(threadMessage.timetoken.toString(), updatedParentChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN))

        parentMessage.removeThread().await()
    }

    @Test
    fun unpinFromParentChannel() = runTest {
        val channel = chat.createChannel(randomString()).await()
        channel.sendText("Parent message ${randomString()}").await()
        delayForHistory()

        val parentMessage = channel.getHistory().await().messages.first()
        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val threadMessage = threadChannel.getHistory().await().messages.first()
        assertIs<ThreadMessage>(threadMessage)
        threadMessage.pinToParentChannel().await()
        delayForHistory()

        val updatedParentChannel = threadMessage.unpinFromParentChannel().await()

        assertIs<Channel>(updatedParentChannel)
        assertNull(updatedParentChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN))

        parentMessage.removeThread().await()
    }

    @Test
    fun onThreadMessageUpdated() = runTest {
        val channel = chat.createChannel(randomString()).await()
        channel.sendText("Parent message ${randomString()}").await()
        delayForHistory()

        val parentMessage = channel.getHistory().await().messages.first()
        val threadMessageText = "Thread reply ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await().threadChannel
        delayForHistory()

        val threadMessage = threadChannel.getHistory().await().messages.first()
        assertIs<ThreadMessage>(threadMessage)

        val newText = "Edited thread reply ${randomString()}"
        val receivedMessage = CompletableDeferred<ThreadMessage>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var closeable: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(threadChannel.id)) {
                closeable = threadMessage.onThreadMessageUpdated { updatedMessage ->
                    receivedMessage.complete(updatedMessage)
                }
            }

            threadMessage.editText(newText).await()

            val result = receivedMessage.await()
            assertIs<ThreadMessage>(result)
            assertEquals(newText, result.text)
            assertEquals(channel.id, result.parentChannelId)
            assertEquals(threadMessage.timetoken, result.timetoken)

            closeable?.close()
        }

        parentMessage.removeThread().await()
    }
}
