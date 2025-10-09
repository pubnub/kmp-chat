package com.pubnub.integration

import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.chat.Event
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.MESSAGE_THREAD_ID_PREFIX
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.listenForEvents
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.HistoryResponse
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageActionType
import com.pubnub.internal.PLATFORM
import com.pubnub.kmp.Uploadable
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import delayForHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class MessageIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun sendingFiles() = runTest {
        if (PLATFORM == "iOS") { // TODO investigate why it doesn't work
            return@runTest
        }
        val fileName = "name.txt"
        val tt = channel01.sendText(
            "message",
            files = listOf(
                InputFile(fileName, "text/plain", generateFileContent(fileName))
            )
        ).await()

        delayForHistory()
        val message: Message = channel01.getMessage(tt.timetoken).await()!!
        assertEquals(1, message.files.size)
        val file = message.files.first()
        assertEquals("text/plain", file.type)
        assertEquals("name.txt", file.name)
        assertTrue(file.url.startsWith("https://"))

        pubnub.deleteFile(channel01.id, file.name, file.id).await()
    }

    @Test
    fun createMessageThenSoftDeleteThenRestore() = runTest {
        val messageText = "messageText_${randomString()}"
        val publishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = publishResult.timetoken
        delayForHistory()
        val message: Message = channel01.getMessage(publishTimetoken).await()!!
        val deletedMessage = message.delete(soft = true).await()!!
        val restoredMessage = deletedMessage.restore().await()
        assertEquals(message.content.text, restoredMessage.content.text)
    }

    @Test
    fun createMessageWithThreadThenSoftDeleteThenRestore() = runTest {
        val messageText = "messageText_${randomString()}"
        val reactionValue = "wow"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        delayForHistory()
        val message: Message = channel01.getMessage(publishTimetoken).await()!!
        val threadChannel: ThreadChannel = message.createThread("message in thread_${randomString()}").await()
        delayForHistory()
        val history: HistoryResponse<ThreadMessage> = threadChannel.getHistory().await()

        val messageWithThread = channel01.getMessage(publishTimetoken).await()
        val messageWithReaction = messageWithThread!!.toggleReaction(reactionValue).await()
        val deletedMessage: Message = messageWithReaction.delete(soft = true).await()!!

        val restoredMessage: Message = deletedMessage.restore().await()
        val restoredThread: ThreadChannel = restoredMessage.getThread().await()
        val historyAfterRestore: HistoryResponse<ThreadMessage> = restoredThread.getHistory().await()

        assertEquals(history.messages.first().content.text, historyAfterRestore.messages.first().content.text)
        assertEquals(history.messages.size, historyAfterRestore.messages.size)
        assertEquals(messageText, restoredMessage.text)
        assertEquals(reactionValue, restoredMessage.actions!!["reactions"]?.keys?.first())
        assertTrue(
            restoredMessage.actions!!["threadRootId"]!!.keys.first()
                .contains("${MESSAGE_THREAD_ID_PREFIX}_${channel01.id}")
        )
        assertEquals(2, restoredMessage.actions!!.size)
    }

    @Test
    fun createMessageWithThreadThenDeleteThread() = runTest(timeout = 100.minutes) {
        // create message with thread
        val messageText = "messageText_${randomString()}"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        delayForHistory()
        val message: Message = channel01.getMessage(publishTimetoken).await()!!
        val threadChannel: ThreadChannel = message.createThread().await()
        // we need to call sendText because addMessageAction is called in sendText that stores details about thread
        threadChannel.sendText("message in thread_${randomString()}").await()

        delayForHistory()
        // we need to call getMessage to get message with indication that it hasThread
        val messageWithThread: Message = channel01.getMessage(publishTimetoken).await()!!

        assertTrue(messageWithThread.hasThread)

        messageWithThread.removeThread().await()
        delayForHistory()
        // we need to call getMessage to get message with indication that it has no Thread
        val messageWithNoThread: Message = channel01.getMessage(publishTimetoken).await()!!

        assertFalse(messageWithNoThread.hasThread)
    }

    @Test
    fun streamUpdatesOn() = runTest {
        chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()

        val tt1 = channel01.sendText("message1").await()
        val tt2 = channel01.sendText("message2").await()
        delayForHistory()

        val message1 = channel01.getMessage(tt1.timetoken).await()!!
        val message2 = channel01.getMessage(tt2.timetoken).await()!!

        val newText = "newText"
        val expectedUpdates = listOf<List<Message>>(
            listOf(message1.asImpl().copy(content = EventContent.TextMessageContent(newText)), message2),
            listOf(
                message1.asImpl().copy(content = EventContent.TextMessageContent(newText)),
                message2.asImpl().copy(
                    content = EventContent.TextMessageContent(
                        newText
                    )
                )
            ),
            listOf(
                message1.asImpl().copy(content = EventContent.TextMessageContent(newText)),
                message2.asImpl().copy(
                    content = EventContent.TextMessageContent(newText),
                    actions = getDeletedActionMap()
                )
            ),
            listOf(
                message1.asImpl().copy(
                    content = EventContent.TextMessageContent(newText),
                    actions = getDeletedActionMap()
                ),
                message2.asImpl().copy(
                    content = EventContent.TextMessageContent(newText),
                    actions = getDeletedActionMap()
                )
            ),
        )
        val actualUpdates = mutableListOf<List<Message>>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                dispose = BaseMessage.streamUpdatesOn(listOf(message1, message2)) { messages ->
                    actualUpdates.add(messages.sortedBy { it.timetoken })
                }
            }

            message1.editText(newText).await()
            message2.editText(newText).await()
            message2.delete(true).await()
            message1.delete(true).await()
            delayInMillis(500)
            message1.delete().await()
            message2.delete().await()

            delayInMillis(1000)
            dispose?.close()
        }
        assertEquals(
            expectedUpdates.map { it.map { Triple(it.timetoken, it.text, it.deleted) } },
            actualUpdates.map { it.map { Triple(it.timetoken, it.text, it.deleted) } }
        )
    }

    @Test
    fun streamUpdatesAndStreamUpdatesOnWithSoftDelete() = runTest {
        chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()

        // Send two messages
        val tt1 = channel01.sendText("message1").await()
        val tt2 = channel01.sendText("message2").await()
        delayForHistory()

        val message1 = channel01.getMessage(tt1.timetoken).await()!!
        val message2 = channel01.getMessage(tt2.timetoken).await()!!

        // Track updates from individual streamUpdates calls
        val message1Updates = mutableListOf<Message>()
        val message2Updates = mutableListOf<Message>()

        // Track updates from streamUpdatesOn
        val streamUpdatesOnCallbacks = mutableListOf<List<Message>>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var disposeStreamUpdates1: AutoCloseable? = null
            var disposeStreamUpdates2: AutoCloseable? = null
            var disposeStreamUpdatesOn: AutoCloseable? = null

            pubnub.awaitSubscribe(listOf(channel01.id)) {
                // Set up individual streamUpdates for message1
                disposeStreamUpdates1 = message1.streamUpdates<Message> { updatedMessage ->
                    message1Updates.add(updatedMessage)
                }

                // Set up individual streamUpdates for message2
                disposeStreamUpdates2 = message2.streamUpdates<Message> { updatedMessage ->
                    message2Updates.add(updatedMessage)
                }

                // Set up streamUpdatesOn for both messages
                disposeStreamUpdatesOn = BaseMessage.streamUpdatesOn(listOf(message1, message2)) { messages ->
                    streamUpdatesOnCallbacks.add(messages.sortedBy { it.timetoken })
                }
            }

            // Soft delete message1 - should trigger callbacks
            message1.delete(soft = true).await()
            delayInMillis(500)

            // Soft delete message2 - should trigger callbacks
            message2.delete(soft = true).await()
            delayInMillis(1000)

            // Clean up listeners
            disposeStreamUpdates1?.close()
            disposeStreamUpdates2?.close()
            disposeStreamUpdatesOn?.close()
        }

        // Verify that streamUpdates callbacks were triggered
        assertEquals(1, message1Updates.size, "message1.streamUpdates should receive 1 update")
        assertEquals(1, message2Updates.size, "message2.streamUpdates should receive 1 update")

        // Verify the deleted flag is set on individual updates
        assertTrue(message1Updates[0].deleted, "message1 should be marked as deleted")
        assertTrue(message2Updates[0].deleted, "message2 should be marked as deleted")

        // Verify timetokens are preserved
        assertEquals(message1.timetoken, message1Updates[0].timetoken)
        assertEquals(message2.timetoken, message2Updates[0].timetoken)

        // Verify that streamUpdatesOn callbacks were triggered
        assertEquals(2, streamUpdatesOnCallbacks.size, "streamUpdatesOn should receive 2 updates (one for each delete)")

        // First callback: message1 deleted, message2 not deleted yet
        val firstUpdate = streamUpdatesOnCallbacks[0]
        assertEquals(2, firstUpdate.size)
        val firstUpdateMsg1 = firstUpdate.find { it.timetoken == message1.timetoken }!!
        val firstUpdateMsg2 = firstUpdate.find { it.timetoken == message2.timetoken }!!
        assertTrue(firstUpdateMsg1.deleted, "message1 should be deleted in first update")
        assertFalse(firstUpdateMsg2.deleted, "message2 should not be deleted in first update")

        // Second callback: both messages deleted
        val secondUpdate = streamUpdatesOnCallbacks[1]
        assertEquals(2, secondUpdate.size)
        val secondUpdateMsg1 = secondUpdate.find { it.timetoken == message1.timetoken }!!
        val secondUpdateMsg2 = secondUpdate.find { it.timetoken == message2.timetoken }!!
        assertTrue(secondUpdateMsg1.deleted, "message1 should be deleted in second update")
        assertTrue(secondUpdateMsg2.deleted, "message2 should be deleted in second update")
    }

    @Test
    fun streamUpdatesOnWithIncrementalSubscription() = runTest {
        // Simulate the React useEffect pattern: receive messages and set up streamUpdatesOn for each as it arrives
        chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()

        val receivedMessages = mutableListOf<Message>()
        val allUpdates = mutableListOf<Pair<Long, String>>() // Track (timetoken, text) updates
        val updateListeners = mutableMapOf<Long, AutoCloseable>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var disconnect: AutoCloseable? = null

            // Set up message listener
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                disconnect = channel01.connect { message ->
                    receivedMessages.add(message)

                    // For each message as it arrives, set up streamUpdatesOn (mimicking React useEffect)
                    if (!updateListeners.containsKey(message.timetoken)) {
                        val stop = BaseMessage.streamUpdatesOn(listOf(message)) { updatedMessages ->
                            updatedMessages.forEach { msg ->
                                allUpdates.add(Pair(msg.timetoken, msg.text))
                            }
                        }
                        updateListeners[message.timetoken] = stop
                    }
                }
            }

            // Publisher sends messages with delays (simulating real-world scenario)
            val message1 = channel01.sendText("Message 0 published").await()
            delayInMillis(500)
            val message2 = channel01.sendText("Message 1 published").await()
            delayInMillis(500)
            val message3 = channel01.sendText("Message 2 published").await()
            delayInMillis(500)
            val message4 = channel01.sendText("Message 3 published").await()
            delayInMillis(500)
            val message5 = channel01.sendText("Message 4 published ").await()

            // Wait for all messages to be received
            delayInMillis(2000)
            assertEquals(5, receivedMessages.size, "Should receive all 5 messages")
            assertEquals(5, updateListeners.size, "Should have 5 streamUpdatesOn listeners")

            // Now edit two of the messages
            val msg1 = receivedMessages[0]
            val msg2 = receivedMessages[1]

            msg1.editText("Edited message 0").await()
            delayInMillis(1000)
            msg2.editText("Edited message 1").await()
            delayInMillis(1000)

            // Verify we received update callbacks
            assertTrue(allUpdates.size >= 2, "Should receive at least 2 updates, got ${allUpdates.size}")

            // Verify the updates contain edited text
            val hasEditedMsg0 = allUpdates.any { it.first == msg1.timetoken && it.second == "Edited message 0" }
            val hasEditedMsg1 = allUpdates.any { it.first == msg2.timetoken && it.second == "Edited message 1" }

            assertTrue(hasEditedMsg0, "Should receive update for edited message 0")
            assertTrue(hasEditedMsg1, "Should receive update for edited message 1")

            // Clean up
            updateListeners.values.forEach { it.close() }
            disconnect?.close()
        }
    }

    @Test
    fun addReactionToMessageThenCheckIfPresent() = runTest {
        val reactionValue = "wow"
        val messageText = "messageText_${randomString()}"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        delayForHistory()
        val message: Message = channel01.getMessage(publishTimetoken).await()!!

        val messageWithReaction = message.toggleReaction(reactionValue).await()

        assertTrue(messageWithReaction.hasUserReaction(reactionValue))

        delayForHistory()
        val messageWithReactionFromHistory: Message = channel01.getHistory(publishTimetoken + 1, publishTimetoken).await().messages.first()

        assertTrue(messageWithReactionFromHistory.hasUserReaction(reactionValue))
    }

    @Test
    fun adminCanSubscribeToInternalChannelRelatedToReportsForSpecificChannelAndCanGetReportedMessageEvent() = runTest {
        val pnPublishResult = channel01.sendText("message1").await()
        val timetoken = pnPublishResult.timetoken
        val reason = "rude"
        val assertionErrorInListener01 = CompletableDeferred<AssertionError?>()
        val channelId = "$INTERNAL_MODERATION_PREFIX${channel01.id}"
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var removeListenerAndUnsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channelId)) {
                removeListenerAndUnsubscribe = chat.listenForEvents<EventContent.Report>(
                    channelId = channelId,
                    callback = { event: Event<EventContent.Report> ->
                        try {
                            // we need to have try/catch here because assertion error will not cause test to fail
                            assertEquals(reason, event.payload.reason)
                            assertEquals(channel01.id, event.payload.reportedMessageChannelId)
                            assertEquals(channelId, event.channelId)
                            assertEquals(someUser.id, event.payload.reportedUserId)
                            assertEquals(timetoken, event.payload.reportedMessageTimetoken)
                            assertionErrorInListener01.complete(null)
                        } catch (e: AssertionError) {
                            assertionErrorInListener01.complete(e)
                        }
                    }
                )
            }
            // when
            delayForHistory()
            val message: Message = channel01.getMessage(timetoken).await()!!
            message.report(reason).await()

            // then
            assertionErrorInListener01.await()?.let { throw it }

            // cleanup
            removeListenerAndUnsubscribe?.close()
        }
    }

    private fun getDeletedActionMap() = mapOf(
        MessageActionType.DELETED.toString() to mapOf(
            MessageActionType.DELETED.toString() to listOf(
                PNFetchMessageItem.Action("anything", 123L)
            )
        )
    )
}

private fun Message.asImpl(): MessageImpl {
    return this as MessageImpl
}

expect fun generateFileContent(fileName: String): Uploadable

expect fun generateFileContentFromImage(): Uploadable
