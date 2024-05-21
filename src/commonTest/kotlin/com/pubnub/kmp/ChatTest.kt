package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.endpoints.objects.channel.GetChannelMetadata
import com.pubnub.api.endpoints.objects.channel.RemoveChannelMetadata
import com.pubnub.api.endpoints.objects.channel.SetChannelMetadata
import com.pubnub.api.endpoints.objects.uuid.GetUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.RemoveUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.SetUUIDMetadata
import com.pubnub.api.endpoints.presence.WhereNow
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.createPNConfiguration
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.pubnub.api.v2.callbacks.Result

class ChatTest {
    private lateinit var objectUnderTest: ChatImpl
    private val chatConfig: ChatConfig = mock(MockMode.strict)

    private val pubnub: PubNub = mock(MockMode.strict)
    private lateinit var pnConfiguration: PNConfiguration
    private val setUUIDMetadataEndpoint: SetUUIDMetadata = mock(MockMode.strict)
    private val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)
    private val getUUIDMetadataEndpoint: GetUUIDMetadata = mock(MockMode.strict)
    private val getChannelMetadataEndpoint: GetChannelMetadata = mock(MockMode.strict)
    private val removeUUIDMetadataEndpoint: RemoveUUIDMetadata = mock(MockMode.strict)
    private val removeChannelMetadataEndpoint: RemoveChannelMetadata = mock(MockMode.strict)
    private val id = "testId"
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val customData = mapOf("testCustom" to "custom")
    private val custom = createCustomObject(customData)
    private val status = "testStatus"
    private val type = "DIRECT"
    private val updated = "timeStamp"
    private val callback: (Result<User>) -> Unit = { }
    private val userId = "myUserId"
    private val subscribeKey = "mySubscribeKey"
    private val publishKey = "myPublishKey"
    private val description = "testDescription"


    @BeforeTest
    fun setUp() {
        pnConfiguration = createPNConfiguration(UserId(userId), subscribeKey, publishKey)
        every { chatConfig.pubnubConfig } returns pnConfiguration
        objectUnderTest = ChatImpl(chatConfig, pubnub)
    }

    @Test
    fun canCreateUser() {
        // given
        every {
            pubnub.setUUIDMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                null,
                null
            )
        } returns setUUIDMetadataEndpoint
        every { setUUIDMetadataEndpoint.async(any()) } returns Unit

        // when
        objectUnderTest.createUser(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type,
            callback = callback
        )

        // then
        verify { pubnub.setUUIDMetadata(id, name, externalId, profileUrl, email, custom, true, null, null) }
    }


    @Test
    fun canUpdateUser() {
        // given
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } returns Unit

        // when
        objectUnderTest.updateUser(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type,
            callback = callback
        )

        // then
        verify { pubnub.getUUIDMetadata(uuid = id, includeCustom = false) }
    }


    @Test
    fun canHardDeleteUser() {
        // given
        val pnUUIDMetadata: PNUUIDMetadata = PNUUIDMetadata(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = customData,
            updated = updated,
            eTag = "eTag",
            type = type,
            status = status
        )
        val pnUuidMetadataResult: PNUUIDMetadataResult = PNUUIDMetadataResult(status = 200, data = pnUUIDMetadata)
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls
                { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(pnUuidMetadataResult))
        }
        every { pubnub.removeUUIDMetadata(any()) } returns removeUUIDMetadataEndpoint
        every { removeUUIDMetadataEndpoint.async(any()) } returns Unit
        val softDeleteFalse = false

        // when
        objectUnderTest.deleteUser(id, softDeleteFalse, callback)

        // then
        verify { pubnub.removeUUIDMetadata(uuid = id) }
    }

    @Test
    fun canSoftDeleteUser() {
        // given
        every {
            pubnub.setUUIDMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns setUUIDMetadataEndpoint
        every { setUUIDMetadataEndpoint.async(any()) } returns Unit

        val pnUUIDMetadata: PNUUIDMetadata = PNUUIDMetadata(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = customData,
            updated = updated,
            eTag = "eTag",
            type = type,
            status = status
        )
        val pnUuidMetadataResult: PNUUIDMetadataResult = PNUUIDMetadataResult(status = 200, data = pnUUIDMetadata)
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(pnUuidMetadataResult))
        }
        val softDelete = true
        val status = "Deleted"
        val includeCustomFalse = false

        // when
        objectUnderTest.deleteUser(id, softDelete, callback)

        // then
        verify {
            pubnub.setUUIDMetadata(
                id,
                name,
                externalId,
                profileUrl,
                email,
                any(),
                includeCustomFalse,
                type,
                status
            )
        }
    }

    @Test
    fun whenIdIsEmptyThenDeleteShouldResultsFailure() {
        // given
        val emptyID = ""
        val softDelete = true
        val callback: (Result<User>) -> Unit = { result: Result<User> ->
        // then
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.deleteUser(emptyID, softDelete, callback)
    }

    @Test
    fun when_wherePresent_executedCallbackResultShouldContainListOfChannels() {
        // given
        val whereNowEndpoint: WhereNow = mock(MockMode.strict)
        val channel01 = "myChannel1"
        val channel02 = "myChannel02"
        val pnWhereNowResult: PNWhereNowResult = PNWhereNowResult(listOf(channel01, channel02))
        every { pubnub.whereNow(any()) } returns whereNowEndpoint
        every {  whereNowEndpoint.async(any())} calls { (callback: Consumer<Result<PNWhereNowResult>>) ->
            callback.accept(Result.success(pnWhereNowResult))
        }

        val callback: (Result<List<String>>) -> Unit = { result: Result<List<String>> ->
        // then
            assertTrue(result.isSuccess)
            assertFalse(result.getOrNull()!!.isEmpty())
            assertTrue(result.getOrNull()!!.contains(channel01))
            assertTrue(result.getOrNull()!!.contains(channel02))
        }

        // when
        objectUnderTest.wherePresent(id, callback)
    }

    @Test
    fun when_isPresent_executedCallbackResultShouldContainsAnswer(){
        // given
        val whereNowEndpoint: WhereNow = mock(MockMode.strict)
        val channel01 = "myChannel1"
        val channel02 = "myChannel02"
        val channelId = "myChannel1"
        val pnWhereNowResult: PNWhereNowResult = PNWhereNowResult(listOf(channel01, channel02))
        every { pubnub.whereNow(any()) } returns whereNowEndpoint
        every {  whereNowEndpoint.async(any())} calls { (callback: Consumer<Result<PNWhereNowResult>>) ->
            callback.accept(Result.success(pnWhereNowResult))
        }

        val callback: (Result<Boolean>) -> Unit = { result: Result<Boolean> ->
        // then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!)
        }

        // when
        objectUnderTest.isPresent(id, channelId, callback)
    }

    @Test
    fun whenChannelIdIsEmptyThen_isPresent_shouldResultsFailure() {
        // given
        val emptyChannelId = ""
        val callback: (Result<Boolean>) -> Unit = { result: Result<Boolean> ->
        // then
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.isPresent(id, emptyChannelId, callback)
    }

    @Test
    fun whenChannelIdIsEmptyResultShouldContainException(){
        // given
        val channelId = ""
        val callback: (Result<Channel>) -> Unit = { result: Result<Channel> ->
        // then
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.updateChannel(id = channelId, callback = callback)
    }

    @Test
    fun shouldResultErrorWhenSetChannelMetadataResultError(){
        val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)
        every { pubnub.setChannelMetadata(any(), any(), any(), any(), any(), any(), any()) } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(Exception("Error calling setChannelMetadata")))
        }
        val callback: (Result<Channel>) -> Unit = { result: Result<Channel> ->
        // then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message!!.contains("Failed to update channel metadata"))
        }

        // when
        objectUnderTest.updateChannel(id = id, callback = callback)
    }

    @Test
    fun shouldResultSuccessWhenSetChannelMetadataResultSuccess(){
        val updatedName = "updatedName"
        val updatedDescription = "updatedDescription"
        val updatedCustom = mapOf("cos" to "cos1")
        val updatedUpdated = "updatedUpdated"
        val updatedType = "GROUP"
        val updatedStatus = "updatedStatus"
        val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)
        every { pubnub.setChannelMetadata(any(), any(), any(), any(), any(), any(), any()) } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(getPNChannelMetadataResult(updatedName, updatedDescription, updatedCustom, updatedUpdated,updatedType, updatedStatus )))
        }
        val callback: (Result<Channel>) -> Unit = { result: Result<Channel> ->
        // then
            assertTrue(result.isSuccess)
            assertEquals(id, result.getOrNull()!!.id)
            assertEquals(updatedName, result.getOrNull()!!.name)
            assertEquals(updatedDescription, result.getOrNull()!!.description)
            assertTrue(result.getOrNull()!!.custom is CustomObject)
            assertEquals(updatedUpdated, result.getOrNull()!!.updated)
            assertEquals(ChannelType.valueOf(updatedType), result.getOrNull()!!.type)
            assertEquals(updatedStatus, result.getOrNull()!!.status)
        }

        // when
        objectUnderTest.updateChannel(id = id, name= name, description = description, callback = callback)
    }

    @Test
    fun canHardDeleteChannel() {
        val callback: (Result<Channel>) -> Unit = {}
        every { pubnub.getChannelMetadata(any(), any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(getPNChannelMetadataResult(name, description, customData, updated, type, status)))
        }
        every { pubnub.removeChannelMetadata(any()) } returns removeChannelMetadataEndpoint
        every { removeChannelMetadataEndpoint.async(any()) } returns Unit

        objectUnderTest.deleteChannel(id = id, soft = false, callback = callback)

        verify { pubnub.removeChannelMetadata(channel = id) }
    }

    @Test
    fun canSoftDeleteChannel() {
        // given
        val callback: (Result<Channel>) -> Unit = {}
        every {
            pubnub.setChannelMetadata(any(), any(), any(), any(), any(), any(), any())
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } returns Unit
        val pnChannelMetadataResult: PNChannelMetadataResult = getPNChannelMetadataResult(name, description, customData, updated, type, status)
        every { pubnub.getChannelMetadata(any(), any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(pnChannelMetadataResult))
        }
        val softDelete = true
        val status = "Deleted"
        val includeCustomFalse = false

        // when
        objectUnderTest.deleteChannel(id, softDelete, callback)

        // then
        verify {
            pubnub.setChannelMetadata(
                channel = id,
                name = name,
                description = description,
                custom = any(),
                includeCustom = includeCustomFalse,
                type = type,
                status = status

            )
        }
    }
    private fun getPNChannelMetadataResult(
        updatedName: String,
        updatedDescription: String,
        updatedCustom: Map<String,Any?>?,
        updatedUpdated: String,
        updatedType: String,
        updatedStatus: String,
    ): PNChannelMetadataResult{
        val pnChannelMetadata = PNChannelMetadata(
            id = id,
            name = updatedName,
            description = updatedDescription,
            custom = updatedCustom,
            updated = updatedUpdated,
            eTag = "updatedETag",
            type = updatedType,
            status = updatedStatus
        )
        return PNChannelMetadataResult(status = 200, data = pnChannelMetadata)
    }
}
