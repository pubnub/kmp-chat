package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.endpoints.objects.channel.GetChannelMetadata
import com.pubnub.api.endpoints.objects.uuid.GetAllUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.GetUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.SetUUIDMetadata
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.utils.PatchValue
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.metadata.AppContextMetadataRepository
import com.pubnub.chat.types.ChannelType
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
import dev.mokkery.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppContextMetadataRepositoryTest {
    private val pubNub: PubNub = mock(MockMode.strict)
    private val chat: ChatInternal = mock(MockMode.strict)
    private val repository = AppContextMetadataRepository(pubNub)

    private val getUUIDMetadataEndpoint: GetUUIDMetadata = mock(MockMode.strict)
    private val getAllUUIDMetadataEndpoint: GetAllUUIDMetadata = mock(MockMode.strict)
    private val setUUIDMetadataEndpoint: SetUUIDMetadata = mock(MockMode.strict)
    private val getChannelMetadataEndpoint: GetChannelMetadata = mock(MockMode.strict)

    private val notFound = PubNubException(statusCode = 404, errorMessage = "Requested object was not found.")
    private val serverError = PubNubException(statusCode = 500, errorMessage = "Server error")

    private fun uuidMetadata() = PNUUIDMetadata(
        id = "user1",
        name = PatchValue.of("Name"),
        externalId = PatchValue.of("ext"),
        profileUrl = PatchValue.of("http://profile"),
        email = PatchValue.of("a@b.c"),
        custom = PatchValue.of(mapOf("k" to "v")),
        updated = PatchValue.of("2024-01-01"),
        eTag = PatchValue.of("etag"),
        type = PatchValue.of("someType"),
        status = PatchValue.of("someStatus"),
    )

    private fun channelMetadata() = PNChannelMetadata(
        id = "channel1",
        name = PatchValue.of("Channel name"),
        description = PatchValue.of("Description"),
        custom = PatchValue.of(mapOf("k" to "v")),
        updated = PatchValue.of("2024-01-01"),
        eTag = PatchValue.of("etag"),
        type = PatchValue.of("group"),
        status = PatchValue.of("someStatus"),
    )

    private fun stubGetUser(result: Result<PNUUIDMetadataResult>) {
        every { pubNub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback.accept(result)
        }
    }

    private fun stubGetChannel(result: Result<PNChannelMetadataResult>) {
        every { pubNub.getChannelMetadata(any(), any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelMetadataResult>>) ->
            callback.accept(result)
        }
    }

    @Test
    fun getUser_mapsAppContextMetadataToUser_andRequestsCustom() = runTest {
        stubGetUser(Result.success(PNUUIDMetadataResult(200, uuidMetadata())))

        val user = repository.getUser(chat, "user1").await()

        assertNotNull(user)
        assertEquals("user1", user.id)
        assertEquals("Name", user.name)
        assertEquals("ext", user.externalId)
        assertEquals("http://profile", user.profileUrl)
        assertEquals("a@b.c", user.email)
        assertEquals(mapOf("k" to "v"), user.custom)
        assertEquals("2024-01-01", user.updated)
        assertEquals("etag", user.eTag)
        assertEquals("someType", user.type)
        assertEquals("someStatus", user.status)
        verify { pubNub.getUUIDMetadata(uuid = "user1", includeCustom = true) }
    }

    @Test
    fun getUser_whenNotFound_returnsNull() = runTest {
        stubGetUser(Result.failure(notFound))

        assertNull(repository.getUser(chat, "user1").await())
    }

    @Test
    fun getUser_whenOtherError_fails() = runTest {
        stubGetUser(Result.failure(serverError))

        val exception = assertFailsWith<PubNubException> { repository.getUser(chat, "user1").await() }

        assertEquals(500, exception.statusCode)
    }

    @Test
    fun getChannel_mapsAppContextMetadataToChannel_andRequestsCustom() = runTest {
        stubGetChannel(Result.success(PNChannelMetadataResult(200, channelMetadata())))

        val channel = repository.getChannel(chat, "channel1").await()

        assertNotNull(channel)
        assertEquals("channel1", channel.id)
        assertEquals("Channel name", channel.name)
        assertEquals("Description", channel.description)
        assertEquals(mapOf("k" to "v"), channel.custom)
        assertEquals("2024-01-01", channel.updated)
        assertEquals(ChannelType.GROUP, channel.type)
        assertEquals("someStatus", channel.status)
        verify { pubNub.getChannelMetadata(channel = "channel1", includeCustom = true) }
    }

    @Test
    fun getChannel_whenNotFound_returnsNull() = runTest {
        stubGetChannel(Result.failure(notFound))

        assertNull(repository.getChannel(chat, "channel1").await())
    }

    @Test
    fun getUsers_assemblesResponseFromAppContextResult() = runTest {
        val next = PNPage.PNNext("nextHash")
        val prev = PNPage.PNPrev("prevHash")
        every { pubNub.getAllUUIDMetadata(any(), any(), any(), any(), any(), any()) } returns getAllUUIDMetadataEndpoint
        every { getAllUUIDMetadataEndpoint.async(any()) } calls { (callback: Consumer<Result<PNUUIDMetadataArrayResult>>) ->
            callback.accept(
                Result.success(
                    PNUUIDMetadataArrayResult(
                        status = 200,
                        data = listOf(uuidMetadata()),
                        totalCount = 7,
                        next,
                        prev
                    )
                )
            )
        }

        val response = repository.getUsers(chat, filter = "name LIKE 'N*'", sort = emptyList(), limit = 1, page = null).await()

        assertEquals(listOf("user1"), response.users.map { it.id })
        assertEquals(7, response.total)
        assertEquals(next, response.next)
        assertEquals(prev, response.prev)
    }

    @Test
    fun getMutedUsers_readsCommaSeparatedListFromCustom() = runTest {
        val metadata = PNUUIDMetadata(id = "PN_PRV.me.mute1", custom = PatchValue.of(mapOf("m" to "a,b,,c")))
        stubGetUser(Result.success(PNUUIDMetadataResult(200, metadata)))

        val mutedUsers = repository.getMutedUsers("PN_PRV.me.mute1").await()

        assertEquals(setOf("a", "b", "c"), mutedUsers)
        verify { pubNub.getUUIDMetadata("PN_PRV.me.mute1", includeCustom = true) }
    }

    @Test
    fun getMutedUsers_whenNotFound_returnsEmptySet() = runTest {
        stubGetUser(Result.failure(notFound))

        assertEquals(emptySet(), repository.getMutedUsers("PN_PRV.me.mute1").await())
    }

    @Test
    fun setMutedUsers_storesCommaSeparatedListInCustom() = runTest {
        val customSlot = Capture.slot<CustomObject>()
        every {
            pubNub.setUUIDMetadata(any(), any(), any(), any(), any(), capture(customSlot), any(), any(), any())
        } returns setUUIDMetadataEndpoint
        every { setUUIDMetadataEndpoint.async(any()) } calls { (callback: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback.accept(Result.success(PNUUIDMetadataResult(200, uuidMetadata())))
        }

        repository.setMutedUsers("PN_PRV.me.mute1", "me", setOf("a", "b")).await()

        @Suppress("UNCHECKED_CAST")
        val storedCustom = (customSlot.get() as? Map<String, Any?>)?.toMap()
        assertEquals(mapOf("m" to "a,b"), storedCustom)
    }
}
