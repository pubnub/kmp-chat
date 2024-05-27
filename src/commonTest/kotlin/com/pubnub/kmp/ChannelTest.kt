package com.pubnub.kmp

import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.MessageType
import com.pubnub.kmp.types.Timer
import com.pubnub.kmp.types.TimerImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @BeforeTest
    fun setUp() {
        objectUnderTest = createChannel(type)
    }

    private fun createChannel(type: ChannelType, timer: Timer = TimerImpl()) = Channel(
        chat = chat,
        time = timer,
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
        every { chat.config } returns chatConfig
        every { chatConfig.typingTimeout } returns 1001
        val typingSent = 1234567890000
        val currentTimeStampInMillis = 1234567890000 + 1
        objectUnderTest = createChannel(type, TimerTestImpl(currentTimeStampInMillis))
        objectUnderTest.setTypingSent(typingSent)
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }
        objectUnderTest.startTyping(callback)

        verify(exactly(0)) { chat.emitEvent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun whenTypingSentAlreadyButTimeoutExpiredStartTypingShouldEmitStartTypingEvent() {
        every { chat.config } returns chatConfig
        val typingTimeout = 1001
        every { chatConfig.typingTimeout } returns typingTimeout
        every { chat.emitEvent(any(), any(), any(), any(), any()) } returns Unit
        val typingSent = 1234567890000
        val currentTimeStampInMillis = 1234567890000 + typingTimeout + ONE_SECOND +1
        objectUnderTest = createChannel(type, TimerTestImpl(currentTimeStampInMillis))
        objectUnderTest.setTypingSent(typingSent)
        val callback: (Result<Unit>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }
        objectUnderTest.startTyping(callback)

        verify {
            chat.emitEvent(
                channel = channelId,
                method = EmitEventMethod.SIGNAL,
                type = "typing",
                payload = EventContent.Typing(true),
                callback = any()
            )
        }
    }

    @Test
    fun whenTypingNotSendShouldEmitStartTypingEvent() {
        every { chat.config } returns chatConfig
        every { chatConfig.typingTimeout } returns 1001
        every { chat.emitEvent(any(), any(), any(), any(), any()) } returns Unit
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
                channel = channelId,
                method = EmitEventMethod.SIGNAL,
                type = "typing",
                payload = EventContent.Typing(true),
                callback = any()
            )
        }
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

        verify(exactly(0)) { chat.emitEvent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun whenStopTypingNotSentShouldEmitStopTypingEvent() {
        val typingSent = 1234567890000
        objectUnderTest.setTypingSent(typingSent)
        every { chat.emitEvent(any(), any(), any(), any(), any()) } returns Unit
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
                channel = channelId,
                method = EmitEventMethod.SIGNAL,
                type = "typing",
                payload = EventContent.Typing(false),
                callback = any()
            )
        }
    }

    private fun createMessage(): Message {
        return Message(
            chat = chat,
            timetoken = "123345",
            content = EventContent.TextMessageContent(
                type = MessageType.TEXT,
                text = "justo",
                files = listOf()
            ),
            channelId = "noster",
            userId = "myUserId",
            actions = mapOf(),
            meta = mapOf()
        )
    }
}

class TimerTestImpl(private val currentTimeStampInMillis: Long): Timer{
    override fun getCurrentTimeStampInMillis(): Long {
        return currentTimeStampInMillis
    }
}