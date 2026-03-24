package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.endpoints.MessageCounts
import com.pubnub.api.endpoints.objects.membership.GetMemberships
import com.pubnub.api.endpoints.objects.membership.ManageMemberships
import com.pubnub.api.models.consumer.history.PNMessageCountResult
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.membership.ChannelMembershipInput
import com.pubnub.api.models.consumer.objects.membership.MembershipInclude
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.utils.PatchValue
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class MembershipTest {
    private val pubNub: PubNub = mock(MockMode.strict)
    private val chat: ChatInternal = mock<ChatInternal>(MockMode.strict).also {
        every { it.pubNub } returns pubNub
    }
    private val user = UserImpl(chat, "user")
    private val channel = ChannelImpl(chat, id = "abc")
    private val lastMessageTimetoken = 123L

    @Test
    fun lastReadMessageTimetoken() {
        val membership =
            MembershipImpl(
                chat,
                channel,
                user,
                mapOf("lastReadMessageTimetoken" to lastMessageTimetoken, "other_stuff" to "some string"),
                null,
                null,
                null,
                null
            )

        assertEquals(lastMessageTimetoken, membership.lastReadMessageTimetoken)
    }

    @Test
    fun getUnreadMessagesCount_when_no_lastReadMessage() = runTest(timeout = 10.seconds) {
        val membership =
            MembershipImpl(
                chat,
                channel,
                user,
                mapOf(),
                null,
                null,
                null,
                null
            )
        assertNull(membership.getUnreadMessagesCount().await())
    }

    @Test
    fun getUnreadMessagesCount() = runTest(timeout = 10.seconds) {
        val membership =
            MembershipImpl(
                chat,
                channel,
                user,
                mapOf("lastReadMessageTimetoken" to lastMessageTimetoken),
                null,
                null,
                null,
                null
            )

        val messageCounts: MessageCounts = mock()
        every { messageCounts.async(any()) } calls { (callback: Consumer<Result<PNMessageCountResult>>) ->
            callback.accept(
                Result.success(
                    PNMessageCountResult(
                        mapOf(
                            channel.id to 5,
                        )
                    )
                )
            )
        }
        every { pubNub.messageCounts(listOf(channel.id), listOf(lastMessageTimetoken)) } returns messageCounts

        assertEquals(5L, membership.getUnreadMessagesCount().await())
    }

    @Test
    fun update_with_custom_preserves_lastReadMessageTimetoken() = runTest(timeout = 10.seconds) {
        val getMembershipsEndpoint: GetMemberships = mock(MockMode.strict)
        val manageMemberships: ManageMemberships = mock(MockMode.strict)
        val existingCustom = mapOf(
            "lastReadMessageTimetoken" to lastMessageTimetoken.toString(),
            "existingKey" to "existingValue"
        )
        val membership = MembershipImpl(chat, channel, user, existingCustom, null, null, null, null)
        val newCustom = createCustomObject(mapOf("role" to "admin"))
        val expectedCustom = createCustomObject(
            mapOf(
                "role" to "admin",
                "lastReadMessageTimetoken" to lastMessageTimetoken.toString()
            )
        )

        every {
            pubNub.getMemberships(
                userId = user.id,
                limit = any<Int?>(),
                page = any<PNPage?>(),
                filter = any<String?>(),
                sort = any<Collection<PNSortKey<PNMembershipKey>>>(),
                include = any<MembershipInclude>()
            )
        } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback.accept(Result.success(createMembershipArrayResult(channel.id)))
        }
        every {
            pubNub.setMemberships(
                channels = any<List<ChannelMembershipInput>>(),
                userId = any<String?>(),
                limit = any<Int?>(),
                page = any<PNPage?>(),
                filter = any<String?>(),
                sort = any<Collection<PNSortKey<PNMembershipKey>>>(),
                include = any<MembershipInclude>()
            )
        } returns manageMemberships
        every { manageMemberships.async(any()) } calls { (callback: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback.accept(Result.success(createMembershipArrayResult(channel.id)))
        }

        membership.update(custom = newCustom).await()

        verify {
            pubNub.setMemberships(
                channels = matching<List<ChannelMembershipInput>> { channels ->
                    val partial = channels.single() as PNChannelMembership.Partial
                    partial.custom == expectedCustom
                },
                userId = user.id,
                limit = any<Int?>(),
                page = any<PNPage?>(),
                filter = any<String?>(),
                sort = any<Collection<PNSortKey<PNMembershipKey>>>(),
                include = any<MembershipInclude>()
            )
        }
    }

    @Test
    fun update_passes_status_type_custom_to_memberships_endpoint() = runTest(timeout = 10.seconds) {
        val getMembershipsEndpoint: GetMemberships = mock(MockMode.strict)
        val manageMemberships: ManageMemberships = mock(MockMode.strict)
        val membershipStatus = "status-1"
        val membershipType = "type-1"
        val membershipCustom = createCustomObject(mapOf("role" to "admin"))
        val membership = MembershipImpl(chat, channel, user, null, null, null, null, null)

        every {
            pubNub.getMemberships(
                userId = user.id,
                limit = any<Int?>(),
                page = any<PNPage?>(),
                filter = any<String?>(),
                sort = any<Collection<PNSortKey<PNMembershipKey>>>(),
                include = any<MembershipInclude>()
            )
        } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback.accept(Result.success(createMembershipArrayResult(channel.id)))
        }
        every {
            pubNub.setMemberships(
                channels = any<List<ChannelMembershipInput>>(),
                userId = any<String?>(),
                limit = any<Int?>(),
                page = any<PNPage?>(),
                filter = any<String?>(),
                sort = any<Collection<PNSortKey<PNMembershipKey>>>(),
                include = any<MembershipInclude>()
            )
        } returns manageMemberships
        every { manageMemberships.async(any()) } calls { (callback: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback.accept(
                Result.success(
                    createMembershipArrayResult(
                        channelId = channel.id,
                        status = membershipStatus,
                        type = membershipType,
                    )
                )
            )
        }

        val result = membership.update(
            status = membershipStatus,
            type = membershipType,
            custom = membershipCustom
        ).await()

        assertEquals(membershipStatus, result.status)
        assertEquals(membershipType, result.type)

        verify {
            pubNub.setMemberships(
                channels = matching<List<ChannelMembershipInput>> { channels ->
                    val partial = channels.single() as PNChannelMembership.Partial
                    partial.channelId == channel.id &&
                        partial.status == membershipStatus &&
                        partial.type == membershipType &&
                        partial.custom == membershipCustom
                },
                userId = user.id,
                limit = any<Int?>(),
                page = any<PNPage?>(),
                filter = "channel.id == '${channel.id}'",
                sort = any<Collection<PNSortKey<PNMembershipKey>>>(),
                include = matching<MembershipInclude> {
                    it.includeCustom &&
                        it.includeStatus &&
                        it.includeType &&
                        it.includeChannel &&
                        it.includeChannelCustom &&
                        it.includeChannelType &&
                        it.includeChannelStatus
                }
            )
        }
    }
}

private fun createMembershipArrayResult(
    channelId: String,
    status: String? = null,
    type: String? = null,
): PNChannelMembershipArrayResult {
    return PNChannelMembershipArrayResult(
        status = 200,
        data = listOf(
            PNChannelMembership(
                channel = PNChannelMetadata(id = channelId),
                custom = null,
                updated = "2024-01-01",
                eTag = "etag",
                status = status?.let { PatchValue.of(it) },
                type = type?.let { PatchValue.of(it) },
            )
        ),
        totalCount = 1,
        next = null,
        prev = null,
    )
}
