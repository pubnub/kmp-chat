package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.endpoints.channel_groups.AllChannelsChannelGroup
import com.pubnub.api.models.consumer.channel_group.PNChannelGroupsAllChannelsResult
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.createPNConfiguration
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channelGroup.ChannelGroupImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.kmp.utils.BaseTest
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelGroupTest : BaseTest() {
    private lateinit var objectUnderTest: ChannelGroupImpl

    private val chat: ChatInternal = mock(MockMode.strict)
    private val pubNub: PubNub = mock(MockMode.strict)
    private val channelGroupId = "test-channel-group"

    private val allChannelsChannelGroupEndpoint: AllChannelsChannelGroup = mock(MockMode.strict)

    @BeforeTest
    fun setUp() {
        every { chat.pubNub } returns pubNub
        every { chat.config } returns ChatConfiguration()
        every { pubNub.configuration } returns createPNConfiguration(
            UserId("testUser"),
            "demo",
            "demo",
            authToken = null
        )

        objectUnderTest = ChannelGroupImpl(
            id = channelGroupId,
            chat = chat
        )
    }

    @Test
    fun listChannels_shouldReturnEmptyResponseWhenNoChannelsInGroup() = runTest {
        // given
        every { pubNub.listChannelsForChannelGroup(channelGroupId) } returns allChannelsChannelGroupEndpoint
        every { allChannelsChannelGroupEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelGroupsAllChannelsResult>>) ->
            callback.accept(Result.success(PNChannelGroupsAllChannelsResult(channels = emptyList())))
        }

        // when
        val result = objectUnderTest.listChannels().await()

        // then
        assertTrue(result.channels.isEmpty())
        assertEquals(0, result.total)
    }

    @Test
    fun listChannels_shouldBuildCorrectFilterWhenChannelsExist() = runTest {
        // given
        val channelIds = listOf("channel1", "channel2")
        val expectedFilter = "id == 'channel1' || id == 'channel2'"

        every { pubNub.listChannelsForChannelGroup(channelGroupId) } returns allChannelsChannelGroupEndpoint
        every { allChannelsChannelGroupEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelGroupsAllChannelsResult>>) ->
            callback.accept(Result.success(PNChannelGroupsAllChannelsResult(channels = channelIds)))
        }

        val channel1 = createChannel("channel1")
        val channel2 = createChannel("channel2")
        val getChannelsResponse = GetChannelsResponse(
            channels = listOf(channel1, channel2),
            next = null,
            prev = null,
            total = 2
        )
        every { chat.getChannels(any(), any(), any(), any()) } returns getChannelsResponse.asFuture()

        // when
        val result = objectUnderTest.listChannels().await()

        // then
        assertEquals(2, result.channels.size)
        verify { chat.getChannels(expectedFilter, any(), any(), any()) }
    }

    @Test
    fun listChannels_shouldCombineFiltersWhenExistingFilterProvided() = runTest {
        // given
        val channelIds = listOf("channel1", "channel2")
        val existingFilter = "name LIKE 'test*'"
        val expectedFilter = "name LIKE 'test*' && (id == 'channel1' || id == 'channel2')"

        every { pubNub.listChannelsForChannelGroup(channelGroupId) } returns allChannelsChannelGroupEndpoint
        every { allChannelsChannelGroupEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelGroupsAllChannelsResult>>) ->
            callback.accept(Result.success(PNChannelGroupsAllChannelsResult(channels = channelIds)))
        }

        val channel1 = createChannel("channel1")
        val channel2 = createChannel("channel2")
        val getChannelsResponse = GetChannelsResponse(
            channels = listOf(channel1, channel2),
            next = null,
            prev = null,
            total = 2
        )
        every { chat.getChannels(any(), any(), any(), any()) } returns getChannelsResponse.asFuture()

        // when
        val result = objectUnderTest.listChannels(filter = existingFilter).await()

        // then
        assertEquals(2, result.channels.size)
        verify { chat.getChannels(expectedFilter, any(), any(), any()) }
    }

    private fun createChannel(channelId: String) = ChannelImpl(
        chat = chat,
        id = channelId,
        name = "Test Channel $channelId",
        custom = null,
        description = null,
        updated = null,
        status = null,
        type = ChannelType.GROUP
    )
}
