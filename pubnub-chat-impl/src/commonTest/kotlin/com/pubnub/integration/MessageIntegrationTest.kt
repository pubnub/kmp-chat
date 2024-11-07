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
import com.pubnub.chat.types.MessageActionType
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun createMessageThenSoftDeleteThenRestore() = runTest {
        val messageText = "messageText_${randomString()}"
        val publishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = publishResult.timetoken
        delayInMillis(200)
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
        val message: Message = channel01.getMessage(publishTimetoken).await()!!
        val threadChannel: ThreadChannel = message.createThread().await()
        // we need to call sendText because addMessageAction is called in sendText that stores details about thread
        threadChannel.sendText("message in thread_${randomString()}").await()

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
    fun createMessageWithThreadThenDeleteThread() = runTest {
        // create message with thread
        val messageText = "messageText_${randomString()}"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        val message: Message = channel01.getMessage(publishTimetoken).await()!!
        println("-=message.timetoken: ${message.timetoken}")
        val threadChannel: ThreadChannel = message.createThread().await()
        println(threadChannel)
        // we need to call sendText because addMessageAction is called in sendText that stores details about thread
        threadChannel.sendText("message in thread_${randomString()}").await()

        // we need to call getMessage to get message with indication that it hasThread
        val messageWithThread: Message = channel01.getMessage(publishTimetoken).await()!!

        assertTrue(messageWithThread.hasThread)

        messageWithThread.removeThread().await()

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
        delayInMillis(1000)

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
    fun addReactionToMessageThenCheckIfPresent() = runTest {
        val reactionValue = "wow"
        val messageText = "messageText_${randomString()}"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val publishTimetoken = pnPublishResult.timetoken
        val message: Message = channel01.getMessage(publishTimetoken).await()!!

        val messageWithReaction = message.toggleReaction(reactionValue).await()

        assertTrue(messageWithReaction.hasUserReaction(reactionValue))
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
                        println("-= in listenForEvents")
                        try {
                            // we need to have try/catch here because assertion error will not cause test to fail
                            assertEquals(reason, event.payload.reason)
                            assertEquals(channelId, event.payload.reportedMessageChannelId)
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
