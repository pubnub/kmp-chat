package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.createJsonElement
import com.pubnub.api.endpoints.objects.channel.SetChannelMetadata
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATOR_DATA_ID
import com.pubnub.chat.internal.INTERNAL_MODERATOR_DATA_TYPE
import com.pubnub.chat.internal.MESSAGE_THREAD_ID_PREFIX
import com.pubnub.chat.internal.PUBNUB_INTERNAL_AUTOMODERATED
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.utils.BaseTest
import com.pubnub.kmp.utils.get
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.Capture
import dev.mokkery.matcher.capture.capture
import dev.mokkery.matcher.capture.get
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    @Test
    fun pin_shouldFetchChannelAndSetPinnedMessageMetadata() {
        val pubNub: PubNub = mock(MockMode.strict)
        val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)
        val messageTimetoken = 9999999L
        val messageChannelId = "testChannelId"
        val message = MessageImpl(
            chat = chat,
            timetoken = messageTimetoken,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId"
        )
        val channel = ChannelImpl(
            chat = chat,
            id = messageChannelId,
            name = "testChannel",
            custom = mapOf("existing" to "value"),
            description = "test description",
            updated = null,
            status = null,
            type = ChannelType.DIRECT
        )

        every { chat.getChannel(messageChannelId) } returns channel.asFuture()
        every { chat.pubNub } returns pubNub

        val customSlot = Capture.slot<CustomObject>()
        every {
            pubNub.setChannelMetadata(
                channel = any(),
                name = any(),
                description = any(),
                custom = capture(customSlot),
                includeCustom = any(),
                type = any(),
                status = any()
            )
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelMetadataResult>>) ->
            callback.accept(Result.success(getPNChannelMetadataResult(updatedId = messageChannelId)))
        }

        message.pin().async { result: Result<Channel> ->
            assertTrue(result.isSuccess)
        }

        val actualCustomMetadata = customSlot.get()
        assertEquals(messageTimetoken.toString(), actualCustomMetadata.get("pinnedMessageTimetoken"))
        assertEquals(messageChannelId, actualCustomMetadata.get("pinnedMessageChannelID"))
    }

    @Test
    fun pin_shouldFailWhenChannelDoesNotExist() {
        val messageChannelId = "nonExistentChannelId"
        val message = MessageImpl(
            chat = chat,
            timetoken = 12345L,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId"
        )

        every { chat.getChannel(messageChannelId) } returns null.asFuture()

        message.pin().async { result: Result<Channel> ->
            assertTrue(result.isFailure)
            assertEquals("Channel does not exist", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun getThread_shouldDelegateToGetThreadChannel() {
        val messageChannelId = "testChannelId"
        val messageTimetoken = 12345L
        val message = MessageImpl(
            chat = chat,
            timetoken = messageTimetoken,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId"
        )
        val mockThreadChannel: ThreadChannel = mock(MockMode.strict)

        every { chat.getThreadChannel(message) } returns mockThreadChannel.asFuture()

        message.getThread().async { result: Result<ThreadChannel> ->
            assertTrue(result.isSuccess)
            assertEquals(mockThreadChannel, result.getOrNull())
        }
    }

    @Test
    fun getThread_shouldPropagateErrorFromGetThreadChannel() {
        val messageChannelId = "testChannelId"
        val messageTimetoken = 12345L
        val message = MessageImpl(
            chat = chat,
            timetoken = messageTimetoken,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId"
        )
        val errorMessage = "This message is not a thread."

        every { chat.getThreadChannel(message) } returns PubNubException(errorMessage).asFuture()

        message.getThread().async { result: Result<ThreadChannel> ->
            assertTrue(result.isFailure)
            assertEquals(errorMessage, result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun createThread_withText_shouldFailWhenChannelIdStartsWithThreadPrefix() {
        val threadChannelId = "${MESSAGE_THREAD_ID_PREFIX}parentChannel_12345"
        val message = MessageImpl(
            chat = chat,
            timetoken = 12345L,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = threadChannelId,
            userId = "testUserId"
        )

        message.createThread("First reply").async { result: Result<ThreadChannel> ->
            assertTrue(result.isFailure)
            assertEquals("Only one level of thread nesting is allowed.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun createThread_withText_shouldFailWhenMessageIsDeleted() {
        val messageChannelId = "testChannelId"
        val deleteActionName = "deleted"
        val message = MessageImpl(
            chat = chat,
            timetoken = 12345L,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId",
            actions = mapOf(
                deleteActionName to mapOf(
                    deleteActionName to listOf(
                        com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action(
                            uuid = "userId",
                            actionTimetoken = 99999L
                        )
                    )
                )
            )
        )

        every { chat.deleteMessageActionName } returns deleteActionName

        message.createThread("First reply").async { result: Result<ThreadChannel> ->
            assertTrue(result.isFailure)
            assertEquals("You cannot create threads on deleted messages.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun createThread_withText_shouldFailWhenThreadAlreadyExists() {
        val messageChannelId = "testChannelId"
        val messageTimetoken = 12345L
        val threadChannelId = "${MESSAGE_THREAD_ID_PREFIX}${messageChannelId}_$messageTimetoken"
        val message = MessageImpl(
            chat = chat,
            timetoken = messageTimetoken,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId"
        )
        val existingChannel = ChannelImpl(
            chat = chat,
            id = threadChannelId,
            name = "Existing Thread",
            custom = null,
            description = null,
            updated = null,
            status = null,
            type = ChannelType.GROUP
        )

        every { chat.deleteMessageActionName } returns "deleted"
        every { chat.getChannel(threadChannelId) } returns existingChannel.asFuture()

        message.createThread("First reply").async { result: Result<ThreadChannel> ->
            assertTrue(result.isFailure)
            assertEquals("Thread for this message already exists.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun removeThread_shouldDelegateToRemoveThreadChannel() {
        val messageChannelId = "testChannelId"
        val messageTimetoken = 12345L
        val message = MessageImpl(
            chat = chat,
            timetoken = messageTimetoken,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId"
        )
        val mockResult = Pair(PNRemoveMessageActionResult(), null as Channel?)

        every { chat.removeThreadChannel(chat, message) } returns mockResult.asFuture()

        message.removeThread().async { result: Result<Pair<PNRemoveMessageActionResult, Channel?>> ->
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun removeThread_shouldPropagateErrorFromRemoveThreadChannel() {
        val messageChannelId = "testChannelId"
        val messageTimetoken = 12345L
        val message = MessageImpl(
            chat = chat,
            timetoken = messageTimetoken,
            content = EventContent.TextMessageContent(text = "test message", files = listOf()),
            channelId = messageChannelId,
            userId = "testUserId"
        )
        val errorMessage = "There is no thread to be deleted."

        every { chat.removeThreadChannel(chat, message) } returns PubNubException(errorMessage).asFuture()

        message.removeThread().async { result: Result<Pair<PNRemoveMessageActionResult, Channel?>> ->
            assertTrue(result.isFailure)
            assertEquals(errorMessage, result.exceptionOrNull()?.message)
        }
    }
}
