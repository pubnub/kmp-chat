package com.pubnub.kmp

import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.types.MessageType
import com.pubnub.kmp.types.TextMessageContent
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

class ChannelTest {
    private lateinit var objectUnderTest: Channel

    private val chat: Chat = mock(MockMode.strict)
    private val id = "testId"
    private val name = "testName"
    private val custom = createCustomObject(mapOf("testCustom" to "custom"))
    private val description = "testDescription"
    private val status = "testStatus"
    private val type = ChannelType.DIRECT
    private val typeString = ChannelType.DIRECT
    private val updated = "testUpdated"
    private val callback: (Result<Channel>) -> Unit = {}

    @BeforeTest
    fun setUp() {
        objectUnderTest = Channel(
            chat = chat,
            id = id,
            name = name,
            custom = custom,
            description = description,
            updated = updated,
            status = status,
            type = type
        )
    }

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

        verify { chat.updateChannel(id, name, custom, description, updated, status, type, callback) }
    }

    @Test
    fun canSoftDeleteChannel() {
        val softDelete = true
        every { chat.deleteChannel(any(), any(), any()) } returns Unit

        objectUnderTest.delete(soft = softDelete, callback)

        verify { chat.deleteChannel(id = id, soft = softDelete, callback = callback) }
    }

    @Test
    fun canForwardMessage() {
        val message = createMessage()
        val callback: (Result<Unit>) -> Unit = { }
        every { chat.forwardMessage(any(), any(), any()) } returns Unit

        objectUnderTest.forwardMessage(message, callback)

        verify { chat.forwardMessage(message, id, callback) }
    }

    private fun createMessage(): Message {
        return Message(
            chat = chat,
            timetoken = "123345",
            content = TextMessageContent(
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
