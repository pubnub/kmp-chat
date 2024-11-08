package com.pubnub.kmp

import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.EventContent
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlin.test.BeforeTest

class MessageTest {
    private lateinit var objectUnderTest: MessageImpl
    private val chat: ChatInternal = mock(MockMode.strict)
    private val timetoken: Long = 123457
    private val messageTest = "this is my message"
    private val messageContent: EventContent.TextMessageContent = EventContent.TextMessageContent(
        text = messageTest,
    )
    private val channelId = "testId"
    private val userId = "myUserId"

    @BeforeTest
    fun setUp() {
        objectUnderTest = MessageImpl(
            chat = chat,
            timetoken = timetoken,
            content = messageContent,
            channelId = channelId,
            userId = userId,
        )
    }
}
