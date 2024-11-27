package com.pubnub.kmp

import com.pubnub.api.createJsonElement
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATOR_DATA_ID
import com.pubnub.chat.internal.INTERNAL_MODERATOR_DATA_TYPE
import com.pubnub.chat.internal.PUBNUB_INTERNAL_AUTOMODERATED
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.utils.BaseTest
import com.pubnub.test.await
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class BaseMessageTest : BaseTest() {
    val chat: ChatInternal = mock()

    @Test
    fun shouldThrowExceptionOnEditTextWhenMessageWasModerated() = runTest {
        every { chat.currentUser } returns UserImpl(chat, "")
        every { chat.editMessageActionName } returns "edit"
        val message =
            MessageImpl(
                chat,
                1L,
                EventContent.TextMessageContent(""),
                "",
                "",
                metaInternal = createJsonElement(mapOf(PUBNUB_INTERNAL_AUTOMODERATED to true))
            )
        val exception = assertFails { message.editText("aaa").await() }
        assertEquals("The automoderated message can no longer be edited", exception.message)
    }

    @Test
    fun shouldNotThrowExceptionOnEditTextWhenMessageWasNotModerated() = runTest {
        every { chat.currentUser } returns UserImpl(chat, "")
        every { chat.editMessageActionName } returns "edit"
        val message = MessageImpl(chat, 1L, EventContent.TextMessageContent(""), "", "")
        val exception = assertFails { message.editText("aaa").await() }
        assertNotEquals("The automoderated message can no longer be edited", exception.message)
    }

    @Test
    fun shouldNotThrowExceptionOnEditTextWhenUserIsModerator() = runTest {
        every { chat.currentUser } returns UserImpl(chat, INTERNAL_MODERATOR_DATA_ID, type = INTERNAL_MODERATOR_DATA_TYPE)
        every { chat.editMessageActionName } returns "edit"
        val message =
            MessageImpl(
                chat,
                1L,
                EventContent.TextMessageContent(""),
                "",
                "",
                metaInternal = createJsonElement(mapOf(PUBNUB_INTERNAL_AUTOMODERATED to true))
            )
        val exception = assertFails { message.editText("aaa").await() }
        assertNotEquals("The automoderated message can no longer be edited", exception.message)
    }
}
