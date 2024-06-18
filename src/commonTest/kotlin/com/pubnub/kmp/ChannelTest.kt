package com.pubnub.kmp

import com.pubnub.api.createJsonElement
import com.pubnub.api.endpoints.FetchMessages
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.HistoryMessageType
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ChannelTest {
    private lateinit var objectUnderTest: Channel

    private val chat: Chat = mock(MockMode.strict)
    private val chatConfig: ChatConfig = mock(MockMode.strict)
    private val channelId = "testId"
    private val name = "testName"
    private val custom = createCustomObject(mapOf("testCustom" to "custom"))
    private val description = "testDescription"
    private val status = "testStatus"
    private val type = ChannelType.DIRECT
    private val updated = "testUpdated"
    private val callback: (Result<Channel>) -> Unit = {}
    private val typingTimeout = 1001.milliseconds


    @BeforeTest
    fun setUp() {
        every { chat.config } returns chatConfig
        every { chatConfig.typingTimeout } returns typingTimeout
        objectUnderTest = createChannel(type)
    }

    private fun createChannel(type: ChannelType, clock: Clock = Clock.System) = Channel(
        chat = chat,
        clock = clock,
        id = channelId,
        name = name,
        custom = custom,
        description = description,
        updated = updated,
        status = status,
        type = type
    )

    @Test
    fun canUpdateChannel() {
        every { chat.updateChannel(any(), any(), any(), any(), any(), any(), any()) } returns objectUnderTest.asFuture()

        objectUnderTest.update(
            name = name,
            custom = custom,
            description = description,
            updated = updated,
            status = status,
            type = type,
        ).async {}

        verify { chat.updateChannel(channelId, name, custom, description, updated, status, type) }
    }

    @Test
    fun canSoftDeleteChannel() {
        val softDelete = true
        every { chat.deleteChannel(any(), any()) } returns objectUnderTest.asFuture()

        objectUnderTest.delete(soft = softDelete).async {}

        verify { chat.deleteChannel(id = channelId, soft = softDelete) }
    }

    @Test
    fun canForwardMessage() {
        val message = createMessage()
        val callback: (Result<Unit>) -> Unit = { }
        every { chat.forwardMessage(any(), any()) } returns Unit.asFuture()

        objectUnderTest.forwardMessage(message).async {}

        verify { chat.forwardMessage(message, channelId) }
    }

    @Test
    fun whenChannelIsPublicStartTypingShouldResultFailure() {
        objectUnderTest = createChannel(ChannelType.PUBLIC)
        objectUnderTest.startTyping().async { result ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Typing indicators are not supported in Public chats.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun whenTypingSentAlreadyStartTypingShouldImmediatelyResultSuccess() {
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        objectUnderTest.startTyping().async { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        verify(exactly(0)) { chat.emitEvent(any(), any()) }
    }

    @Test
    fun whenTypingSentAlreadyButTimeoutExpiredStartTypingShouldEmitStartTypingEvent() {
        every { chat.emitEvent(any(), any()) } returns PNPublishResult(1L).asFuture()
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis =
            typingSent.plus(typingTimeout).plus(MINIMAL_TYPING_INDICATOR_TIMEOUT).plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        objectUnderTest.startTyping().async  { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        verify {
            chat.emitEvent(
                channel = channelId,
                payload = EventContent.Typing(true)
            )
        }
    }

    @Test
    fun whenTypingNotSendShouldEmitStartTypingEvent() {
        every { chat.emitEvent(any(), any()) } returns PNPublishResult(1L).asFuture()
        val callback: (Result<Unit>) -> Unit = { result: Result<Unit> ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        // when
        objectUnderTest.startTyping().async {}

        // then
        verify {
            chat.emitEvent(
                channel = channelId,
                payload = EventContent.Typing(true)
            )
        }
    }

    @Test
    fun whenTypingTimoutSetToZeroShouldNotEmitSignalWithinFirstSecond() {
        every { chatConfig.typingTimeout } returns 0.milliseconds
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }
        objectUnderTest.startTyping().async {}

        verify(exactly(0)) { chat.emitEvent(any(), any()) }
    }

    @Test
    fun whenChannelIsPublicShouldResultFailure() {
        objectUnderTest = createChannel(ChannelType.PUBLIC)
        val callback: (Result<Unit>) -> Unit = { result ->
            assertTrue(result.isFailure)
            assertEquals("Typing indicators are not supported in Public chats.", result.exceptionOrNull()?.message)
        }
        objectUnderTest.stopTyping().async {}
    }

    @Test
    fun whenStopTypingAlreadySentStopTypingShouldImmediatelyResultSuccess() {
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        objectUnderTest.stopTyping().async {}

        verify(exactly(0)) { chat.emitEvent(any(), any()) }
    }

    @Test
    fun whenTimeElapsedShouldNotSendSignalButReturnImmediately() {
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(2001.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        objectUnderTest.stopTyping().async {}

        verify(exactly(0)) { chat.emitEvent(any(), any()) }

    }

    @Test
    fun whenTimeNotElapsedShouldSendSignal() {
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        every { chat.emitEvent(any(), any()) } returns PNPublishResult(1L).asFuture()
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }
        // when
        objectUnderTest.stopTyping().async {}

        // then
        verify {
            chat.emitEvent(
                channel = channelId,
                payload = EventContent.Typing(false)
            )
        }
    }

    private fun createMessage(): Message {
        return Message(
            chat = chat,
            timetoken = 123345,
            content = EventContent.TextMessageContent(
                text = "justo",
                files = listOf()
            ),
            channelId = "noster",
            userId = "myUserId",
            actions = mapOf(),
            meta = mapOf()
        )
    }

    @Test
    fun canCallIsPresent() {
        every { chat.isPresent(any(), any()) } returns true.asFuture()

        val callback = { _: Result<Boolean> -> }
        objectUnderTest.isPresent(
            userId = "user"
        )

        verify { chat.isPresent("user", channelId) }
    }

    @Test
    fun canCallWhoIsPresent() {
        every { chat.whoIsPresent(any()) } returns emptyList<String>().asFuture()

        val callback = { _: Result<Collection<String>> -> }
        objectUnderTest.whoIsPresent().async {}

        verify { chat.whoIsPresent(channelId) }
    }

    @Test
    fun getHistoryReturnsMessages() {
        // given
        val startToken = 1L
        val endToken = 2000L
        val pubNub: PubNub = mock(MockMode.strict)
        val fetchMessages: FetchMessages = mock(MockMode.strict)

        every { chat.pubNub } returns pubNub
        every {
            pubNub.fetchMessages(
                any(),
                PNBoundedPage(startToken, endToken, 25),
                any(),
                any(),
                any(),
                any()
            )
        } returns fetchMessages

        val message1 = "message text"
        val message2 = "second message"
        val user1 = "myUser"
        val user2 = "myUser2"
        val timetoken1 = 10000L
        val timetoken2 = 10001L

        every { fetchMessages.async(any()) } calls { (callback1: Consumer<Result<PNFetchMessagesResult>>) ->
            callback1.accept(
                Result.success(
                    PNFetchMessagesResult(
                        mapOf(
                            channelId to listOf(
                                PNFetchMessageItem(user1, createJsonElement(mapOf("type" to "text", "text" to message1)), null,
                                    timetoken1, null, HistoryMessageType.Message, null),
                                PNFetchMessageItem(user2, createJsonElement(mapOf("text" to message2, "files" to null)), null,
                                    timetoken2, null, HistoryMessageType.Message, null),
                            )
                        ), null
                    )
                )
            )
        }

        // when
        objectUnderTest.getHistory(startToken, endToken).async {
            // then
            assertTrue { it.isSuccess }
            it.onSuccess { result ->
                assertEquals(
                    listOf(
                        Message(
                            chat,
                            timetoken1,
                            EventContent.TextMessageContent(message1),
                            channelId,
                            user1,
                            null,
                            null
                        ),
                        Message(
                            chat,
                            timetoken2,
                            EventContent.TextMessageContent(message2),
                            channelId,
                            user2,
                            null,
                            null
                        ),
                    ), result
                )
            }
        }
    }

    @Test
    fun sendTextAllParametersArePassedToPublish() {
        every { chat.publish(any(), any(), any(), any(), any(), any(), any()) } returns PNPublishResult(1L).asFuture()
        val messageText = "someText"
        val message = Message(chat, 1000L, EventContent.TextMessageContent(messageText), channelId, "some user", null, null)
        val mentionedUser1 = "mention1"
        val referencedChannel1 = "referenced1"
        val userName = "someName"
        val channelName = "someChannel"
        val link = "some link"
        val ttl = 100
        objectUnderTest.sendText(
            text = messageText,
            meta = mapOf("custom_meta" to "custom"),
            shouldStore = true,
            usePost = false,
            ttl = ttl,
            mentionedUsers = mapOf(0 to MessageMentionedUser(mentionedUser1, userName)),
            referencedChannels = mapOf(0 to MessageReferencedChannel(referencedChannel1, channelName)),
            textLinks = listOf(TextLink(1, 20, link)),
            quotedMessage = message,
            null, // todo when files work
        ).async {}

        verify { chat.publish(
            channelId,
            EventContent.TextMessageContent(messageText),
            mapOf(
                "custom_meta" to "custom",
                "mentionedUsers" to mapOf(
                    "0" to mapOf("id" to mentionedUser1, "name" to userName)
                ),
                "referencedChannels" to mapOf(
                    "0" to mapOf("id" to referencedChannel1, "name" to channelName)
                ),
                "textLinks" to listOf(
                    mapOf(
                        "startIndex" to 1,
                        "endIndex" to 20,
                        "link" to link
                    )
                ),
                "quotedMessage" to mapOf(
                    "timetoken" to message.timetoken,
                    "text" to message.text,
                    "userId" to message.userId
                )
            ),
            true,
            false,
            true,
            ttl
        ) }
    }
}
