package com.pubnub.kmp

import com.pubnub.api.v2.callbacks.Result
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
        // given
        every { chat.updateChannel(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // when
        objectUnderTest.update(
            name = name,
            custom = custom,
            description = description,
            updated = updated,
            status = status,
            type = type,
            callback = callback
        )

        // then
        verify { chat.updateChannel(id, name, custom, description, updated, status, type, callback) }
    }
}
