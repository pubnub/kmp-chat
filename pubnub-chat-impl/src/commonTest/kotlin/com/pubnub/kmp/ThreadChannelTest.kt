package com.pubnub.kmp

import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.utils.BaseTest
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreadChannelTest : BaseTest() {
    lateinit var objectUnderTest: ThreadChannelImpl

    private val parentMessage: Message = mock(MockMode.strict)
    private val parentMessageId: String = "parentMessageId01"
    private val chat: ChatInternal = mock(MockMode.strict)
    private val threadChannelId = "threadTestId"
    private val threadChannelName = "threadTestName"
    private val custom: Map<String, Any?> = mapOf("testCustom" to "custom")
    private val description = "testDescription"
    private val status = "testStatus"
    private val updated = "testUpdated"
    private val type = ChannelType.DIRECT
    private val threadCreated = true

    @BeforeTest
    fun setUp() {
        every { parentMessage.channelId } returns parentMessageId
        objectUnderTest = createThreadChannel()
    }

    private fun createThreadChannel(): ThreadChannelImpl {
        return ThreadChannelImpl(
            parentMessage = parentMessage,
            chat = chat,
            clock = Clock.System,
            id = threadChannelId,
            name = threadChannelName,
            custom = custom,
            description = description,
            updated = updated,
            status = status,
            type = type,
            threadCreated = threadCreated
        )
    }

    @Test
    fun pinMessageToParentChannel() {
        every { chat.getChannel(parentMessageId) } returns PNFuture { callback ->
            callback.accept(Result.success(null))
        }

        val threadMessage: ThreadMessage = mock(MockMode.strict)

        objectUnderTest.pinMessageToParentChannel(threadMessage).async { result: Result<Channel> ->
            assertTrue { result.isFailure }
            assertEquals("Parent channel doesn't exist.", result.exceptionOrNull()?.message)
        }
    }
}
