package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.createJsonElement
import com.pubnub.api.endpoints.FetchMessages
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.history.HistoryMessageType
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
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
        every { chat.updateChannel(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        objectUnderTest.update(
            name = name,
            custom = custom,
            description = description,
            updated = updated,
            status = status,
            type = type,
            callback = callback
        )

        verify { chat.updateChannel(channelId, name, custom, description, updated, status, type, callback) }
    }

    @Test
    fun canSoftDeleteChannel() {
        val softDelete = true
        every { chat.deleteChannel(any(), any(), any()) } returns Unit

        objectUnderTest.delete(soft = softDelete, callback)

        verify { chat.deleteChannel(id = channelId, soft = softDelete, callback = callback) }
    }

    @Test
    fun canForwardMessage() {
        val message = createMessage()
        val callback: (Result<Unit>) -> Unit = { }
        every { chat.forwardMessage(any(), any(), any()) } returns Unit

        objectUnderTest.forwardMessage(message, callback)

        verify { chat.forwardMessage(message, channelId, callback) }
    }

    @Test
    fun whenChannelIsPublicStartTypingShouldResultFailure() {
        objectUnderTest = createChannel(ChannelType.PUBLIC)
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Typing indicators are not supported in Public chats.", result.exceptionOrNull()?.message)
        }

        objectUnderTest.startTyping(callback)
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
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }
        objectUnderTest.startTyping(callback)

        verify(exactly(0)) { chat.emitEvent(any(), any(), any()) }
    }

    @Test
    fun whenTypingSentAlreadyButTimeoutExpiredStartTypingShouldEmitStartTypingEvent() {
        every { chat.emitEvent(any(), any(), any()) } returns Unit
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
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }
        objectUnderTest.startTyping(callback)

        verify {
            chat.emitEvent(
                channelOrUser = channelId,
                payload = EventContent.Typing(true),
                callback = any()
            )
        }
    }

    @Test
    fun whenTypingNotSendShouldEmitStartTypingEvent() {
        every { chat.emitEvent(any(), any(), any()) } returns Unit
        val callback: (Result<Unit>) -> Unit = { result: Result<Unit> ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        // when
        objectUnderTest.startTyping(callback)

        // then
        verify {
            chat.emitEvent(
                channelOrUser = channelId,
                payload = EventContent.Typing(true),
                callback = any()
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
        objectUnderTest.startTyping(callback)

        verify(exactly(0)) { chat.emitEvent(any(), any(), any()) }
    }

    @Test
    fun whenChannelIsPublicShouldResultFailure() {
        objectUnderTest = createChannel(ChannelType.PUBLIC)
        val callback: (Result<Unit>) -> Unit = { result ->
            assertTrue(result.isFailure)
            assertEquals("Typing indicators are not supported in Public chats.", result.exceptionOrNull()?.message)
        }
        objectUnderTest.stopTyping(callback)
    }

    @Test
    fun whenStopTypingAlreadySentStopTypingShouldImmediatelyResultSuccess() {
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        objectUnderTest.stopTyping(callback)

        verify(exactly(0)) { chat.emitEvent(any(), any(), any()) }
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

        objectUnderTest.stopTyping(callback)

        verify(exactly(0)) { chat.emitEvent(any(), any(), any()) }

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
        every { chat.emitEvent(any(), any(), any()) } returns Unit
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }
        // when
        objectUnderTest.stopTyping(callback)

        // then
        verify {
            chat.emitEvent(
                channelOrUser = channelId,
                payload = EventContent.Typing(false),
                callback = any()
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
        every { chat.isPresent(any(), any(), any()) } returns Unit

        val callback = { _: Result<Boolean> -> }
        objectUnderTest.isPresent(
            userId = "user",
            callback = callback
        )

        verify { chat.isPresent("user", channelId, callback) }
    }

    @Test
    fun canCallWhoIsPresent() {
        every { chat.whoIsPresent(any(), any()) } returns Unit

        val callback = { _: Result<Collection<String>> -> }
        objectUnderTest.whoIsPresent(
            callback = callback
        )

        verify { chat.whoIsPresent(channelId, callback) }
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

        every { fetchMessages.async(any()) } calls { (callback1: Consumer<Result<PNFetchMessagesResult>>) ->
            callback1.accept(
                Result.success(
                    PNFetchMessagesResult(
                        mapOf(
                            channelId to listOf(
                                PNFetchMessageItem("myUser", createJsonElement(mapOf("type" to "text", "text" to "message text")), null, 10000L, null, HistoryMessageType.Message, null),
                                PNFetchMessageItem("myUser2", createJsonElement(mapOf("text" to "second message", "files" to null)), null, 10001L, null, HistoryMessageType.Message, null),
                            )
                        ), null
                    )
                )
            )
        }

        // when
        objectUnderTest.getHistory(startToken, endToken) {
            // then
            assertTrue { it.isSuccess }
            it.onSuccess { result ->
                assertEquals(
                    listOf(
                        Message(
                            chat,
                            10000L,
                            EventContent.TextMessageContent("message text"),
                            channelId,
                            "myUser",
                            null,
                            null
                        ),
                        Message(
                            chat,
                            10001L,
                            EventContent.TextMessageContent("second message"),
                            channelId,
                            "myUser2",
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
        every { chat.publish(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        val message = Message(chat, 1000L, EventContent.TextMessageContent("some text"), channelId, "some user", null, null)
        objectUnderTest.sendText(
            text = "someText",
            meta = mapOf("custom_meta" to "custom"),
            shouldStore = true,
            usePost = false,
            replicate = true,
            ttl = 100,
            mentionedUsers = mapOf(0 to MessageMentionedUser("mention1", "someName")),
            referencedChannels = mapOf(0 to MessageReferencedChannel("referenced1", "someChannel")),
            textLinks = listOf(TextLink(1, 20, "some link")),
            quotedMessage = message,
            null, // todo when files work
        ) {}

        verify { chat.publish(
            channelId,
            EventContent.TextMessageContent("someText"),
            mapOf(
                "custom_meta" to "custom",
                "mentionedUsers" to mapOf(
                    "0" to mapOf("id" to "mention1", "name" to "someName")
                ),
                "referencedChannels" to mapOf(
                    "0" to mapOf("id" to "referenced1", "name" to "someChannel")
                ),
                "textLinks" to listOf(
                    mapOf(
                        "startIndex" to 1,
                        "endIndex" to 20,
                        "link" to "some link"
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
            100,
            any()
        ) }
    }
}
