package com.pubnub.kmp

import com.pubnub.api.Endpoint
import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.async
import com.pubnub.api.endpoints.objects.uuid.GetUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.RemoveUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.SetUUIDMetadata
import com.pubnub.api.endpoints.presence.WhereNow
import com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.v2.PNConfiguration
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

class ChatTest {
    private lateinit var objectUnderTest: ChatImpl
    private val chatConfig: ChatConfig = mock(MockMode.strict)

    private val pubnub: PubNub = mock(MockMode.strict)
    private lateinit var pnConfiguration: PNConfiguration
    private val setUUIDMetadataEndpoint: SetUUIDMetadata = mock(MockMode.strict)
    private val getUUIDMetadataEndpoint: GetUUIDMetadata = mock(MockMode.strict)
    private val removeUUIDMetadataEndpoint: RemoveUUIDMetadata = mock(MockMode.strict)
    private val id = "testId"
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val custom = mapOf("testCustom" to "custom")
    private val status = "testStatus"
    private val type = "testType"
    private val updated = "timeStamp"
    private val callback: (Result<User>) -> Unit = { }
    private val userId = "myUserId"
    private val subscribeKey = "mySubscribeKey"
    private val publishKey = "myPublishKey"


    @BeforeTest
    fun setUp() {
        pnConfiguration = createPNConfiguration(UserId(userId), subscribeKey)
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
            updated = updated,
            callback = callback
        )

        // then
        verify { pubnub.getUUIDMetadata(uuid = id, includeCustom = false) }
    }


    @Test
    fun canHardDeleteUser() {
        // given
        // this is fine :| don't know why IntelliJ mark it as problematic
        val pnUUIDMetadata: PNUUIDMetadata = PNUUIDMetadata(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            updated = updated,
            eTag = "eTag",
            type = type,
            status = status
        )
        val pnUuidMetadataResult: PNUUIDMetadataResult = PNUUIDMetadataResult(status = 200, data = pnUUIDMetadata)
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls
                { (callback1: (Result<PNUUIDMetadataResult>) -> Unit) ->
            callback1(Result.success(pnUuidMetadataResult))
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

        // this is fine :| , I don't know why IntelliJ marks it as problematic
        val pnUUIDMetadata: PNUUIDMetadata = PNUUIDMetadata(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            updated = updated,
            eTag = "eTag",
            type = type,
            status = status
        )
        val pnUuidMetadataResult: PNUUIDMetadataResult = PNUUIDMetadataResult(status = 200, data = pnUUIDMetadata)
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: (Result<PNUUIDMetadataResult>) -> Unit) ->
            callback1(Result.success(pnUuidMetadataResult))
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
                custom,
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
        every {  whereNowEndpoint.async(any())} calls { (callback: (Result<PNWhereNowResult>) -> Unit) ->
            callback(Result.success(pnWhereNowResult))
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
        every {  whereNowEndpoint.async(any())} calls { (callback: (Result<PNWhereNowResult>) -> Unit) ->
            callback(Result.success(pnWhereNowResult))
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
            assertEquals("Channel ID is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.isPresent(id, emptyChannelId, callback)
    }
}
