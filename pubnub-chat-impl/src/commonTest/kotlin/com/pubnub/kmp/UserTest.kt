package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.endpoints.objects.membership.GetMemberships
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.membership.MembershipInclude
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.utils.PatchValue
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.kmp.utils.FakeChat
import com.pubnub.test.randomString
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class UserTest {
    private lateinit var objectUnderTest: User
    private val chat: ChatInternal = mock(MockMode.strict)
    private val chatConfig: ChatConfiguration = mock(MockMode.strict)
    private val pubnubConfig: PNConfiguration = mock(MockMode.strict)
    private val pubNub: PubNub = mock(MockMode.strict)
    private val id = "testId"
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val customData = mapOf("testCustom" to "custom")
    private val custom = createCustomObject(customData)
    private val status = "testStatus"
    private val type = "testType"
    private val updated = "testUpdated"
    private val channelId = "channelId01"

    @BeforeTest
    fun setUp() {
        every { chatConfig.typingTimeout } returns 2000.milliseconds
        every { chat.config } returns chatConfig
        every { chat.pubNub } returns pubNub
        every { pubNub.configuration } returns pubnubConfig
        every { pubnubConfig.secretKey } returns ""
        objectUnderTest = createUser(chat)
    }

    private fun createUser(chat: ChatInternal) = UserImpl(
        chat = chat,
        id = id,
        name = name,
        externalId = externalId,
        profileUrl = profileUrl,
        email = email,
        custom = customData,
        status = status,
        type = type,
        updated = updated,
    )

    @Test
    fun canSoftDeleteUser() {
        // given
        val softDelete = true
        val chat = object : FakeChat(chatConfig, pubNub) {
            var soft: Boolean? = null

            override fun deleteUser(id: String, soft: Boolean): PNFuture<User?> {
                this.soft = soft
                return objectUnderTest.asFuture()
            }
        }
        val sut = createUser(chat)

        // when
        sut.delete(softDelete).async {}

        // then
        assertEquals(softDelete, chat.soft)
    }

    @Test
    fun canHardDeleteUser() {
        // given
        val softDelete = false
        val chat = object : FakeChat(chatConfig, pubNub) {
            var softDeleted: Boolean? = null
            var deletedUserId: String? = null

            override fun deleteUser(id: String, soft: Boolean): PNFuture<User?> {
                this.softDeleted = soft
                this.deletedUserId = id
                return objectUnderTest.asFuture()
            }
        }
        val sut = createUser(chat)

        // when
        sut.delete(softDelete).async {}

        // then
        assertEquals(softDelete, chat.softDeleted)
        assertEquals(sut.id, chat.deletedUserId)
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
                type = any()
            )
        } returns objectUnderTest.asFuture()

        // when
        objectUnderTest.update(
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type
        ).async {}

        // then
        verify { chat.updateUser(id, name, externalId, profileUrl, email, custom, status, type) }
    }

    @Test
    fun canWherePresent() {
        // given
        val callback: (Result<List<String>>) -> Unit = {}
        every { chat.wherePresent(any()) } returns emptyList<String>().asFuture()

        // when
        objectUnderTest.wherePresent().async {}

        // then
        verify { chat.wherePresent(id) }
    }

    @Test
    fun getMembershipsShouldResultFailureWhenPubNubReturnsError() {
        // given
        val limit = 10
        val page = PNPage.PNNext("nextPageHash")
        val filter = "channel.name LIKE '*super*'"
        val errorMessage = "Strange exception"
        val sort = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))
        val getMembershipsEndpoint: GetMemberships = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every {
            pubNub.getMemberships(
                userId = any(),
                limit = any(),
                page = any(),
                filter = any(),
                sort = any(),
                include = any()
            )
        } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback1.accept(Result.failure(Exception(errorMessage)))
        }

        // when
        objectUnderTest.getMemberships(limit = limit, page = page, filter = filter, sort = sort).async { result ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Failed to retrieve getMembership data.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun getMembershipsShouldResultSuccessWhenPubNubReturnsSuccess() {
        // given
        val limit = 10
        val page = PNPage.PNNext("nextPageHash")
        val filter = "channel.name LIKE '*super*'"
        val expectedFilter = "!(channel.id LIKE '$INTERNAL_MODERATION_PREFIX*') && ($filter)"
        val sort = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))
        val getMembershipsEndpoint: GetMemberships = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every {
            pubNub.getMemberships(
                userId = any(),
                limit = any(),
                page = any(),
                filter = any(),
                sort = any(),
                include = any(),
            )
        } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback1.accept(Result.success(getPNChannelMembershipArrayResult()))
        }

        // when
        objectUnderTest.getMemberships(limit = limit, page = page, filter = filter, sort = sort).async { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()!!.memberships.size)
            assertEquals(channelId, result.getOrNull()!!.memberships.first().channel.id)
        }

        // then
        verify {
            pubNub.getMemberships(
                userId = id,
                limit = limit,
                page = page,
                filter = expectedFilter,
                sort = sort,
                include = matching<MembershipInclude> {
                    it.includeCustom && it.includeStatus && it.includeType && it.includeTotalCount &&
                        it.includeChannel && it.includeChannelCustom && it.includeChannelType &&
                        it.includeChannelStatus
                }
            )
        }
    }

    @Test
    fun canGetRestrictionsWithNoChannelProvided() {
        val noChannelProvided = null
        val limit = 1
        val page: PNPage = PNPage.PNNext("nextPageHash")
        val sort = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))
        val getMemberships: GetMemberships = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every {
            pubNub.getMemberships(
                any(),
                any(),
                any(),
                any(),
                any(),
                include = any(),
            )
        } returns getMemberships

        (objectUnderTest as UserImpl).getRestrictions(
            channel = noChannelProvided,
            limit = limit,
            page = page,
            sort = sort
        )

        val expectedFilter = "channel.id LIKE 'PUBNUB_INTERNAL_MODERATION_*'"
        verify {
            pubNub.getMemberships(
                userId = id,
                limit = limit,
                page = page,
                filter = expectedFilter,
                sort = sort,
                include = matching<MembershipInclude> {
                    it.includeCustom && it.includeStatus && it.includeType && it.includeTotalCount &&
                        it.includeChannel && it.includeChannelCustom && it.includeChannelType &&
                        it.includeChannelStatus
                }
            )
        }
    }

    @Test
    fun canGetRestrictionsByChannel() {
        val channelId = "channelId"
        val channel = ChannelImpl(chat = chat, id = channelId)
        val limit = 1
        val page: PNPage? = PNPage.PNNext("nextPageHash")
        val sort = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))
        val getMemberships: GetMemberships = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every {
            pubNub.getMemberships(
                any(),
                any(),
                any(),
                any(),
                any(),
                include = any(),
            )
        } returns getMemberships

        (objectUnderTest as UserImpl).getRestrictions(channel = channel, limit = limit, page = page, sort = sort)

        val expectedFilter = "channel.id == 'PUBNUB_INTERNAL_MODERATION_channelId'"
        verify {
            pubNub.getMemberships(
                userId = id,
                limit = limit,
                page = page,
                filter = expectedFilter,
                sort = sort,
                include = matching<MembershipInclude> {
                    it.includeCustom && it.includeStatus && it.includeType && it.includeTotalCount &&
                        it.includeChannel && it.includeChannelCustom &&
                        it.includeChannelType && it.includeChannelStatus
                }
            )
        }
    }

    @Test
    fun shouldThrowExceptionWhenSecretKeyIsNotSet() {
        val channel = ChannelImpl(chat = chat, id = "channelId")
        objectUnderTest.setRestrictions(channel).async { result ->
            assertTrue(result.isFailure)
            assertEquals(
                "Moderation restrictions can only be set by clients initialized with a Secret Key.",
                result.exceptionOrNull()?.message
            )
        }
    }

    @Test
    fun plus() {
        val user = createUser(chat)
        val expectedUser = user.copy(name = randomString(), email = randomString())

        val newUser = user + PNUUIDMetadata(
            expectedUser.id,
            name = PatchValue.of(expectedUser.name),
            email = PatchValue.of(expectedUser.email)
        )

        assertEquals(expectedUser, newUser)
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
