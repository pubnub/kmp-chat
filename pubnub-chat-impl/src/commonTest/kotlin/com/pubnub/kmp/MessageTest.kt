package com.pubnub.kmp

import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Message
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.EventContent
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun shouldReturnErrorWhenRestoringMessageThatDoesNotExists() {
        val actionsWithEntryIndicatingThatMessageHasBeenDeleted: Map<String, Map<String, List<Action>>> =
            mapOf("deleted" to mapOf("deleted" to listOf(Action("user1", 1234L))))
        objectUnderTest = MessageImpl(
            chat = chat,
            timetoken = timetoken,
            content = messageContent,
            channelId = channelId,
            userId = userId,
            actions = actionsWithEntryIndicatingThatMessageHasBeenDeleted
        )
        every { chat.deleteMessageActionName } returns "DELETED"

        objectUnderTest.restore().async { result: Result<Message> ->
            assertTrue(result.isFailure)
            assertEquals("This message has not been deleted", result.exceptionOrNull()?.message)
            println(result)
        }
    }
}
