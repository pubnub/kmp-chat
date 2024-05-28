package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.endpoints.objects.membership.GetMemberships
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.v2.callbacks.Consumer
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.membership.MembershipsResponse
import dev.mokkery.answering.calls
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class UserTest {
    private lateinit var objectUnderTest: User
    private val chat: Chat = mock(MockMode.strict)
    private val chatConfig: ChatConfig = mock(MockMode.strict)
    private val pubNub: PubNub = mock(MockMode.strict)
    private val id = "testId"
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val custom = createCustomObject(mapOf("testCustom" to "custom"))
    private val status = "testStatus"
    private val type = "testType"
    private val updated = "testUpdated"
    private val callbackUser: (Result<User>) -> Unit = { }
    private val channelId = "channelId01"

    @BeforeTest
    fun setUp() {
        every { chatConfig.typingTimeout } returns 2000.milliseconds
        every { chat.config } returns chatConfig
        objectUnderTest = User(
            chat = chat,
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type,
            updated = updated,
        )
    }

    @Test
    fun canSoftDeleteUser() {
        // given
        val softDeleteTrue = true
        every { chat.deleteUser(any(), any(), any())} returns Unit

        // when
        objectUnderTest.delete(softDeleteTrue, callbackUser)

        // then
        verify { chat.deleteUser(id, softDeleteTrue, callbackUser) }
    }

    @Test
    fun canHardDeleteUser() {
        // given
        val softDeleteFalse = false
        every { chat.deleteUser(any(), any(), any()) } returns Unit

        // when
        objectUnderTest.delete(soft = softDeleteFalse, callbackUser)

        // then
        verify { chat.deleteUser(id, softDeleteFalse, callbackUser) }
    }

    @Test
    fun canUpdateUser() {
        // given
        every {
            chat.updateUser(
                id = any(),
                name = any(),
                externalId = any(),
                profileUrl = any(),
                email = any(),
                custom = any(),
                status = any(),
                type = any(),
                callback = any()
            )
        } returns Unit

        // when
        objectUnderTest.update(
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type,
            callback = callbackUser
        )

        // then
        verify { chat.updateUser(id, name, externalId, profileUrl, email, custom, status, type, callbackUser) }
    }

    @Test
    fun canWherePresent() {
        // given
        val callback: (Result<List<String>>) -> Unit = {}
        every { chat.wherePresent(any(), any()) } returns Unit

        // when
        objectUnderTest.wherePresent(callback)

        // then
        verify { chat.wherePresent(id, callback) }
    }

    @Test
    fun canIsPresentOn() {
        // given
        val callback: (Result<Boolean>) -> Unit = {}
        every { chat.isPresent(any(), any(), any()) } returns Unit

        // when
        objectUnderTest.isPresentOn(channelId = channelId, callback = callback)

        // then
        verify { chat.isPresent(id, channelId, callback) }
    }

    @Test
    fun getMembershipsShouldResultFailureWhenPubNubReturnsError() {
        // given
        val limit = 10
        val page = PNPage.PNNext("nextPageHash")
        val filter = "channel.name LIKE '*super*'"
        val errorMessage = "Strange exception"
        val sort = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))
        val callback: (Result<MembershipsResponse>) -> Unit = { result ->
        // then
            assertTrue(result.isFailure)
            assertEquals("Failed to retrieve getMembership data.", result.exceptionOrNull()?.message)
        }
        val getMembershipsEndpoint: GetMemberships = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every { pubNub.getMemberships(
            uuid = any(),
            limit = any(),
            page = any(),
            filter = any(),
            sort = any(),
            includeCount = any(),
            includeCustom = any(),
            includeChannelDetails = any()
        ) } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback1.accept(Result.failure(Exception(errorMessage)))
        }

        // when
        objectUnderTest.getMemberships(limit = limit, page = page, filter = filter, sort = sort, callback = callback)

        // then
        verify { pubNub.getMemberships(
            uuid = id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true,
            includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM
        ) }
    }

    @Test
    fun getMembershipsShouldResultSuccessWhenPubNubReturnsSuccess() {
        // given
        val limit = 10
        val page = PNPage.PNNext("nextPageHash")
        val filter = "channel.name LIKE '*super*'"
        val sort = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))
        val callback: (Result<MembershipsResponse>) -> Unit = { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()!!.memberships.size)
            assertEquals(channelId, result.getOrNull()!!.memberships[0].channel.id)
        }
        val getMembershipsEndpoint: GetMemberships = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every { pubNub.getMemberships(
            uuid = any(),
            limit = any(),
            page = any(),
            filter = any(),
            sort = any(),
            includeCount = any(),
            includeCustom = any(),
            includeChannelDetails = any()
        ) } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback1.accept(Result.success(getPNChannelMembershipArrayResult()))
        }

        // when
        objectUnderTest.getMemberships(limit = limit, page = page, filter = filter, sort = sort, callback = callback)

        // then
        verify { pubNub.getMemberships(
            uuid = id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true,
            includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM
        ) }
    }

    private fun getPNChannelMembershipArrayResult(): PNChannelMembershipArrayResult {
        val channelMetadata = PNChannelMetadata(
            id = channelId,
            name = null,
            description = null,
            custom = null,
            updated = null,
            eTag = null,
            type = null,
            status = null
        )

        val channelMembership = PNChannelMembership(
            channel = channelMetadata,
            custom = null,
            updated = "2024-05-20T14:50:19.972361Z",
            eTag = "AZO/t53al7m8fw",
            status = null
        )

        val data: List<PNChannelMembership> = mutableListOf(channelMembership)
        return PNChannelMembershipArrayResult(
            status = 200,
            data = data,
            totalCount = 1,
            next = null,
            prev = null
        )
    }
}
