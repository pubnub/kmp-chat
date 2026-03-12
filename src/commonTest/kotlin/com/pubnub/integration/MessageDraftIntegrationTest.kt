package com.pubnub.integration

import com.pubnub.chat.Channel
import com.pubnub.chat.MentionTarget
import com.pubnub.chat.Message
import com.pubnub.chat.MessageDraft.UserSuggestionSource
import com.pubnub.chat.MessageElement
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.LogLevel
import com.pubnub.chat.createMessageDraft
import com.pubnub.chat.createThreadMessageDraft
import com.pubnub.chat.getMessageElements
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.test.BaseIntegrationTest
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// These integration tests live in the root module (not pubnub-chat-impl) because they test
// Channel.createMessageDraft() and Message.createThreadMessageDraft() extension functions
// defined in src/commonMain/kotlin/com/pubnub/chat/mediators.kt, which is part of the root
// module's public API surface and cannot be moved to pubnub-chat-impl.
class MessageDraftIntegrationTest : BaseIntegrationTest() {
    private val channelsToRemove = mutableSetOf<String>()
    private val usersToRemove = mutableSetOf<String>()

    val chat: ChatImpl by lazy(LazyThreadSafetyMode.NONE) {
        ChatImpl(ChatConfiguration(logLevel = LogLevel.DEBUG), pubnub).also { usersToRemove.add(it.currentUser.id) }
    }

    val channel01: Channel by lazy(LazyThreadSafetyMode.NONE) {
        ChannelImpl(
            chat = chat,
            id = randomString() + "!_=-@",
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        ).also { channelsToRemove.add(it.id) }
    }

    @AfterTest
    override fun after() {
        runTest {
            val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
            try {
                supervisorScope {
                    usersToRemove.forEach {
                        launch(exceptionHandler) {
                            pubnub.removeUUIDMetadata(it).await()
                        }
                    }
                    channelsToRemove.forEach {
                        launch(exceptionHandler) {
                            pubnub.removeChannelMetadata(it).await()
                        }
                    }
                }
            } finally {
                chat.destroy()
            }
        }
        super.after()
    }

    @Test
    fun messageDraft_send() = runTest {
        // Send a preliminary message to use as a quoted message
        val quoteText = "Message to quote"
        val publishResult = channel01.sendText(quoteText).await()
        val history = channel01.getHistory(
            startTimetoken = publishResult.timetoken + 1,
            endTimetoken = publishResult.timetoken,
            count = 1
        ).await()
        val quotedMsg = history.messages.first()

        val draft = channel01.createMessageDraft(isTypingIndicatorTriggered = false)
        draft.update("Some text with a mention")
        draft.addMention(17, 7, MentionTarget.User("someUser"))
        draft.quotedMessage = quotedMsg
        val message = CompletableDeferred<Message>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                unsubscribe = channel01.onMessageReceived {
                    if (!message.isCompleted) {
                        message.complete(it)
                        unsubscribe?.close()
                    }
                }
            }
            draft.send().await()
            val received = message.await()
            val elements = received.getMessageElements()

            assertEquals(
                listOf(
                    MessageElement.PlainText("Some text with a "),
                    MessageElement.Link("mention", MentionTarget.User("someUser"))
                ),
                elements
            )

            assertNotNull(received.quotedMessage)
            assertEquals(quotedMsg.timetoken, received.quotedMessage!!.timetoken)
            assertEquals(quoteText, received.quotedMessage!!.text)
            assertEquals(quotedMsg.userId, received.quotedMessage!!.userId)
        }
    }

    @Test
    fun createMessageDraft_defaultParams() = runTest {
        val draft = channel01.createMessageDraft()

        assertEquals(UserSuggestionSource.CHANNEL, draft.userSuggestionSource)
        assertTrue(draft.isTypingIndicatorTriggered)
        assertEquals(10, draft.userLimit)
        assertEquals(10, draft.channelLimit)

        val messageText = "Hello from default draft ${randomString()}"
        draft.update(messageText)

        val received = CompletableDeferred<Message>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                unsubscribe = channel01.onMessageReceived {
                    if (!received.isCompleted) {
                        received.complete(it)
                        unsubscribe?.close()
                    }
                }
            }
            draft.send().await()
            val msg = received.await()
            assertEquals(messageText, msg.text)
        }
    }

    @Test
    fun createMessageDraft_customParams() = runTest {
        val draft = channel01.createMessageDraft(
            userSuggestionSource = UserSuggestionSource.GLOBAL,
            isTypingIndicatorTriggered = false,
            userLimit = 5,
            channelLimit = 3
        )

        assertEquals(UserSuggestionSource.GLOBAL, draft.userSuggestionSource)
        assertEquals(false, draft.isTypingIndicatorTriggered)
        assertEquals(5, draft.userLimit)
        assertEquals(3, draft.channelLimit)

        draft.update("Custom params mention")
        draft.addMention(14, 7, MentionTarget.User("someUser"))

        val received = CompletableDeferred<Message>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                unsubscribe = channel01.onMessageReceived {
                    if (!received.isCompleted) {
                        received.complete(it)
                        unsubscribe?.close()
                    }
                }
            }
            draft.send().await()
            val msg = received.await()
            val elements = msg.getMessageElements()

            assertEquals(
                listOf(
                    MessageElement.PlainText("Custom params "),
                    MessageElement.Link("mention", MentionTarget.User("someUser"))
                ),
                elements
            )
        }
    }

    @Test
    fun createThreadMessageDraft_sendToThread() = runTest {
        val channel = chat.createChannel(randomString()).await()
        channelsToRemove.add(channel.id)
        val parentMessageText = "Parent message ${randomString()}"
        channel.sendText(parentMessageText).await()
        withContext(Dispatchers.Default) { delay(1.seconds) }
        val history = channel.getHistory().await()
        val parentMessage = history.messages.first()

        val draft = parentMessage.createThreadMessageDraft(isTypingIndicatorTriggered = false).await()
        assertIs<ThreadChannel>(draft.channel)

        val threadReplyText = "Thread reply ${randomString()}"
        draft.update(threadReplyText)

        val received = CompletableDeferred<Message>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(draft.channel.id)) {
                unsubscribe = draft.channel.onMessageReceived {
                    if (!received.isCompleted) {
                        received.complete(it)
                        unsubscribe?.close()
                    }
                }
            }
            draft.send().await()
            val msg = received.await()
            assertEquals(threadReplyText, msg.text)
        }

        parentMessage.removeThread().await()
    }
}
