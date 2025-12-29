package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.endpoints.objects.channel.SetChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.message.ThreadMessageImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadMessageTest : BaseTest() {
    private val chat: ChatInternal = mock(MockMode.strict)
    private val pubNub: PubNub = mock(MockMode.strict)
    private val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)

    private val parentChannelId = "parentChannelId"
    private val threadChannelId = "PUBNUB_INTERNAL_THREAD_parentChannelId_123456"
    private val threadMessageTimetoken = 9999999L
    private val threadMessageUserId = "threadMessageUserId"

    private fun createThreadMessage(
        timetoken: Long = threadMessageTimetoken,
        channelId: String = threadChannelId,
        parentChannel: String = parentChannelId
    ): ThreadMessageImpl {
        return ThreadMessageImpl(
            chat = chat,
            parentChannelId = parentChannel,
            timetoken = timetoken,
            content = EventContent.TextMessageContent(text = "thread message text", files = listOf()),
            channelId = channelId,
            userId = threadMessageUserId
        )
    }

    private fun createParentChannel(): ChannelImpl {
        return ChannelImpl(
            chat = chat,
            id = parentChannelId,
            name = "parentChannel",
            custom = mapOf("existing" to "value"),
            description = "parent channel description",
            updated = null,
            status = null,
            type = ChannelType.DIRECT
        )
    }

    @Test
    fun pinToParentChannel_shouldFetchParentChannelAndSetPinnedMessageMetadata() {
        val threadMessage = createThreadMessage()
        val parentChannel = createParentChannel()

        every { chat.getChannel(parentChannelId) } returns parentChannel.asFuture()
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
            callback.accept(Result.success(getPNChannelMetadataResult(updatedId = parentChannelId)))
        }

        threadMessage.pinToParentChannel().async { result: Result<Channel> ->
            assertTrue(result.isSuccess)
        }

        val actualCustomMetadata = customSlot.get()
        assertEquals(threadMessageTimetoken.toString(), actualCustomMetadata.get("pinnedMessageTimetoken"))
        assertEquals(threadChannelId, actualCustomMetadata.get("pinnedMessageChannelID"))
    }

    @Test
    fun pinToParentChannel_shouldFailWhenParentChannelDoesNotExist() {
        val threadMessage = createThreadMessage()

        every { chat.getChannel(parentChannelId) } returns null.asFuture()

        threadMessage.pinToParentChannel().async { result: Result<Channel> ->
            assertTrue(result.isFailure)
            assertEquals("Parent channel doesn't exist.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun unpinFromParentChannel_shouldFetchParentChannelAndRemovePinnedMessageMetadata() {
        val threadMessage = createThreadMessage()
        val parentChannel = ChannelImpl(
            chat = chat,
            id = parentChannelId,
            name = "parentChannel",
            custom = mapOf(
                "existing" to "value",
                "pinnedMessageTimetoken" to threadMessageTimetoken.toString(),
                "pinnedMessageChannelID" to threadChannelId
            ),
            description = "parent channel description",
            updated = null,
            status = null,
            type = ChannelType.DIRECT
        )

        every { chat.getChannel(parentChannelId) } returns parentChannel.asFuture()
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
            callback.accept(Result.success(getPNChannelMetadataResult(updatedId = parentChannelId)))
        }

        threadMessage.unpinFromParentChannel().async { result: Result<Channel> ->
            assertTrue(result.isSuccess)
        }

        val actualCustomMetadata = customSlot.get()
        assertNull(actualCustomMetadata.get("pinnedMessageTimetoken"))
        assertNull(actualCustomMetadata.get("pinnedMessageChannelID"))
    }

    @Test
    fun unpinFromParentChannel_shouldFailWhenParentChannelDoesNotExist() {
        val threadMessage = createThreadMessage()

        every { chat.getChannel(parentChannelId) } returns null.asFuture()

        threadMessage.unpinFromParentChannel().async { result: Result<Channel> ->
            assertTrue(result.isFailure)
            assertEquals("Parent channel doesn't exist.", result.exceptionOrNull()?.message)
        }
    }
}
