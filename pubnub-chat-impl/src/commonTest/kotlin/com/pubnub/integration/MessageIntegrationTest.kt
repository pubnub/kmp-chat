package com.pubnub.integration

import com.pubnub.api.PubNubException
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
import com.pubnub.chat.types.CreateThreadResult
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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Ignore
    @Test // todo enable test once deleteMessage from channel containing "!_=-@" is clarified
    fun restore_hardDeletedMessage_shouldFail() = runTest {
        // given - a message that is hard deleted
        val messageText = "Message to be hard deleted ${randomString()}"
        val publishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = publishResult.timetoken
        delayForHistory()

        val message = channel01.getMessage(publishTimetoken).await()!!

        // when - hard delete the message (soft=false)
        val deletedMessage = message.delete(soft = false).await()

        // then - should return null (hard deleted messages cannot be restored)
        assertNull(deletedMessage, "Hard deleted message should return null")

        delayForHistory()

        // verify - message should not exist in history
        val messageAfterDelete = channel01.getMessage(publishTimetoken).await()
        assertNull(messageAfterDelete, "Hard deleted message should not be retrievable from history")
    }

    @Test
    fun getThread_beforeCreation_shouldThrowException() = runTest {
        // given - a message without a thread
        val messageText = "Message without thread ${randomString()}"
        val publishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = publishResult.timetoken
        delayForHistory()

        val message = channel01.getMessage(publishTimetoken).await()!!

        // then - message should not have a thread
        assertFalse(message.hasThread, "Message should not have a thread initially")

        // when - try to get thread that doesn't exist
        // then - should throw exception
        val exception = assertFailsWith<PubNubException> {
            message.getThread().await()
        }

        assertEquals(
            true,
            exception.message?.contains("This message is not a thread", ignoreCase = true),
            "Exception should indicate no thread exists. Got: ${exception.message}"
        )
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
        val messageWithReactionFromHistory: Message =
            channel01.getHistory(publishTimetoken + 1, publishTimetoken).await().messages.first()

        assertTrue(messageWithReactionFromHistory.hasUserReaction(reactionValue))
    }

    @Test
    fun toggleReaction_multipleTogglesCycles_shouldMaintainCorrectState() = runTest {
        // given - a message
        val messageText = "Message for reaction test ${randomString()}"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        delayForHistory()

        var message = channel01.getMessage(publishTimetoken).await()!!
        val reactionValue = "like"

        // when - toggle reaction multiple times rapidly
        // Toggle ON
        message = message.toggleReaction(reactionValue).await()
        assertTrue(message.hasUserReaction(reactionValue), "After first toggle, should have reaction")

        // Toggle OFF
        message = message.toggleReaction(reactionValue).await()
        assertFalse(message.hasUserReaction(reactionValue), "After second toggle, should not have reaction")

        // Toggle ON again
        message = message.toggleReaction(reactionValue).await()
        assertTrue(message.hasUserReaction(reactionValue), "After third toggle, should have reaction again")

        // Toggle OFF again
        message = message.toggleReaction(reactionValue).await()
        assertFalse(message.hasUserReaction(reactionValue), "After fourth toggle, should not have reaction again")

        // then - verify final state from history
        delayForHistory()
        val finalMessage = channel01.getMessage(publishTimetoken).await()!!
        assertFalse(
            finalMessage.hasUserReaction(reactionValue),
            "Final message from history should not have reaction (even number of toggles)"
        )
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

    @Test
    fun threadChannel_getHistory_withCountLimit_shouldReturnLimitedResults() = runTest {
        // given - create a message and thread with multiple messages
        val messageText = "Parent message_${randomString()}"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        delayForHistory()

        val message: Message = channel01.getMessage(publishTimetoken).await()!!
        val threadChannel: ThreadChannel = message.createThread("First thread message_${randomString()}").await()

        // add more messages to thread
        val additionalMessageCount = 5
        val threadMessageTexts = (2..additionalMessageCount).map {
            "Thread message $it - ${randomString()}"
        }

        threadMessageTexts.forEach { text ->
            threadChannel.sendText(text).await()
        }

        delayForHistory()

        // when - fetch with count limit of 3
        val limitedHistory: HistoryResponse<ThreadMessage> = threadChannel.getHistory(count = 3).await()

        // then - should get exactly 3 messages
        assertEquals(3, limitedHistory.messages.size, "Should get 3 messages when count=3")
        assertTrue(limitedHistory.isMore, "Should indicate more messages available")

        // when - fetch all with large count
        val allMessages: HistoryResponse<ThreadMessage> = threadChannel.getHistory(count = 100).await()

        // then - should get all thread messages (including the first one from createThread)
        assertTrue(
            allMessages.messages.size >= additionalMessageCount,
            "Should get at least $additionalMessageCount thread messages"
        )

        // verify all our test messages are present
        val allTexts = allMessages.messages.map { it.text }.toSet()
        assertTrue(
            threadMessageTexts.all { it in allTexts },
            "All thread messages should be in history"
        )

        // cleanup - we need to re-fetch the message to see update state that contains info that it "hasThread"
        val messageWithThread = channel01.getMessage(publishTimetoken).await()!!
        messageWithThread.removeThread().await()
    }

    @Test
    fun createThreadWithResult_shouldReturnUpdatedParentMessageWithHasThreadTrue() = runTest {
        // given - a message without a thread
        val messageText = "Parent message_${randomString()}"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        delayForHistory()

        val message: Message = channel01.getMessage(publishTimetoken).await()!!
        assertFalse(message.hasThread, "Message should not have thread initially")

        // when - create thread using createThreadWithResult
        val threadText = "First thread message_${randomString()}"
        val result: CreateThreadResult = message.createThreadWithResult(threadText).await()

        // then - result should contain both threadChannel and updated parentMessage
        val threadChannel = result.threadChannel
        val updatedParentMessage = result.parentMessage

        // verify threadChannel is valid
        assertTrue(threadChannel.id.contains(MESSAGE_THREAD_ID_PREFIX), "Thread channel ID should contain prefix")
        assertEquals(message.channelId, threadChannel.parentChannelId, "Parent channel ID should match")

        // verify parentMessage has hasThread = true (this is the key improvement!)
        assertTrue(
            updatedParentMessage.hasThread,
            "Returned parentMessage should have hasThread=true immediately without re-fetching"
        )
        assertEquals(message.timetoken, updatedParentMessage.timetoken, "Parent message timetoken should be preserved")
        assertEquals(message.text, updatedParentMessage.text, "Parent message text should be preserved")

        // cleanup
        updatedParentMessage.removeThread().await()
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
