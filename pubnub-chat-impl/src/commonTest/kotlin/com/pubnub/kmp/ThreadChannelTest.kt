package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.endpoints.objects.channel.SetChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.utils.Clock
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.utils.BaseTest
import com.pubnub.kmp.utils.get
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.Capture
import dev.mokkery.matcher.capture.capture
import dev.mokkery.matcher.capture.get
import dev.mokkery.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun pinMessageToParentChannel_shouldFailWhenParentChannelDoesNotExist() {
        every { chat.getChannel(parentMessageId) } returns PNFuture { callback ->
            callback.accept(Result.success(null))
        }

        val threadMessage: ThreadMessage = mock(MockMode.strict)

        objectUnderTest.pinMessageToParentChannel(threadMessage).async { result: Result<Channel> ->
            assertTrue { result.isFailure }
            assertEquals("Parent channel doesn't exist.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun pinMessageToParentChannel_shouldSetPinnedMessageMetadataOnParentChannel() {
        val pubNub: PubNub = mock(MockMode.strict)
        val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)
        val threadMessageTimetoken = 9999999L
        val threadMessageChannelId = threadChannelId

        val parentChannel = ChannelImpl(
            chat = chat,
            id = parentMessageId,
            name = "parentChannel",
            custom = mapOf("existing" to "value"),
            description = "parent channel description",
            updated = null,
            status = null,
            type = ChannelType.DIRECT
        )

        val threadMessage: ThreadMessage = mock(MockMode.strict)
        every { threadMessage.timetoken } returns threadMessageTimetoken
        every { threadMessage.channelId } returns threadMessageChannelId

        every { chat.getChannel(parentMessageId) } returns parentChannel.asFuture()
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
            callback.accept(Result.success(getPNChannelMetadataResult(updatedId = parentMessageId)))
        }

        objectUnderTest.pinMessageToParentChannel(threadMessage).async { result: Result<Channel> ->
            assertTrue(result.isSuccess)
        }

        val actualCustomMetadata = customSlot.get()
        assertEquals(threadMessageTimetoken.toString(), actualCustomMetadata.get("pinnedMessageTimetoken"))
        assertEquals(threadMessageChannelId, actualCustomMetadata.get("pinnedMessageChannelID"))
    }

    @Test
    fun unpinMessageFromParentChannel_shouldFailWhenParentChannelDoesNotExist() {
        every { chat.getChannel(parentMessageId) } returns PNFuture { callback ->
            callback.accept(Result.success(null))
        }

        objectUnderTest.unpinMessageFromParentChannel().async { result: Result<Channel> ->
            assertTrue { result.isFailure }
            assertEquals("Parent channel doesn't exist.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun unpinMessageFromParentChannel_shouldRemovePinnedMessageMetadataFromParentChannel() {
        val pubNub: PubNub = mock(MockMode.strict)
        val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)

        val parentChannel = ChannelImpl(
            chat = chat,
            id = parentMessageId,
            name = "parentChannel",
            custom = mapOf(
                "existing" to "value",
                "pinnedMessageTimetoken" to "9999999",
                "pinnedMessageChannelID" to threadChannelId
            ),
            description = "parent channel description",
            updated = null,
            status = null,
            type = ChannelType.DIRECT
        )

        every { chat.getChannel(parentMessageId) } returns parentChannel.asFuture()
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
            callback.accept(Result.success(getPNChannelMetadataResult(updatedId = parentMessageId)))
        }

        objectUnderTest.unpinMessageFromParentChannel().async { result: Result<Channel> ->
            assertTrue(result.isSuccess)
        }

        val actualCustomMetadata = customSlot.get()
        assertNull(actualCustomMetadata.get("pinnedMessageTimetoken"))
        assertNull(actualCustomMetadata.get("pinnedMessageChannelID"))
    }
}
