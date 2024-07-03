package com.pubnub.integration

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.membership.ChannelMembershipInput
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.kmp.Channel
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.Event
import com.pubnub.kmp.Membership
import com.pubnub.kmp.User
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_UPDATE_USER_METADATA
import com.pubnub.kmp.error.PubNubErrorMessage.USER_NOT_EXIST
import com.pubnub.kmp.membership.MembershipsResponse
import com.pubnub.kmp.message.GetUnreadMessagesCounts
import com.pubnub.kmp.message.MarkAllMessageAsReadResponse
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.JoinResult
import com.pubnub.kmp.utils.cyrb53a
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ChatIntegrationTest : BaseChatIntegrationTest() {

    @Test
    fun createUser() = runTest {
        val user = chat.createUser(someUser).await()

        assertEquals(someUser, user.copy(updated = null, lastActiveTimestamp = null))
        assertNotNull(user.updated)
    }

    @Test
    fun updateUser() = runTest {
        val user = chat.createUser(someUser).await()
        val expectedUser = user.copy(
            name = randomString(),
            externalId = randomString(),
            profileUrl = randomString(),
            email = randomString(),
            custom = mapOf(
                randomString() to randomString()
            ),
            type = randomString(),
            updated = null
        )

        val updatedUser = chat.updateUser(
            expectedUser.id,
            expectedUser.name,
            expectedUser.externalId,
            expectedUser.profileUrl,
            expectedUser.email,
            expectedUser.custom?.let { createCustomObject(it) },
            expectedUser.status,
            expectedUser.type
        ).await()

        assertEquals(expectedUser, updatedUser.copy(updated = null, lastActiveTimestamp = null))
        assertNotNull(updatedUser.updated)
    }

    @Test
    fun updateUser_doesntExist() = runTest {
        val e = assertFailsWith<PubNubException> {
            chat.updateUser(someUser.id, name = randomString()).await()
        }

        assertEquals(FAILED_TO_UPDATE_USER_METADATA.message, e.message)
        assertEquals(USER_NOT_EXIST.message, e.cause?.message)
    }

    @Test
    fun createDirectConversation() = runTest {
        // when
        val result = chat.createDirectConversation(someUser).await()

        // then
        val sortedUsers = listOf(chat.currentUser.id, someUser.id).sorted()
        assertEquals("direct${cyrb53a("${sortedUsers[0]}&${sortedUsers[1]}")}", result.channel.id)

        assertEquals(chat.currentUser, result.hostMembership.user.copy(updated = null, lastActiveTimestamp = null))
        assertEquals(someUser, result.inviteeMembership.user.copy(updated = null, lastActiveTimestamp = null))

        assertEquals(result.channel, result.hostMembership.channel)
        assertEquals(result.channel, result.inviteeMembership.channel)

    }

    @Test
    fun createGroupConversation() = runTest {
        val otherUsers = listOf(User(chat, randomString()), User(chat, randomString()))

        // when
        val result = chat.createGroupConversation(otherUsers).await()

        // then
        assertEquals(chat.currentUser, result.hostMembership.user.copy(updated = null, lastActiveTimestamp = null))
        assertEquals(otherUsers.size, result.inviteeMemberships.size)
        result.inviteeMemberships.forEach { inviteeMembership ->
            assertEquals(
                otherUsers.first { it.id == inviteeMembership.user.id },
                inviteeMembership.user.copy(updated = null, lastActiveTimestamp = null)
            )
            assertEquals(result.channel, inviteeMembership.channel)
        }

        assertEquals(result.channel, result.hostMembership.channel)
    }

    @Test
    fun can_markAllMessagesAsRead() = runTest {
        //create two membership for user one with "lastReadMessageTimetoken" and second without.
        val lastReadMessageTimetokenValue: Long = 17195737006492403
        val custom: CustomObject =
            createCustomObject(mapOf("lastReadMessageTimetoken" to lastReadMessageTimetokenValue))
        val channelId01 = channel01.id
        val channelId02 = channel02.id
        val membership01: ChannelMembershipInput = PNChannelMembership.Partial(channelId01, custom)
        val membership02: ChannelMembershipInput = PNChannelMembership.Partial(channelId02)
        chat.pubNub.setMemberships(listOf(membership01, membership02), chat.currentUser.id).await()

        //to each channel add two messages(we want to check if last message will be taken by fetchMessages with limit = 1)
        channel01.sendText("message01In$channelId01").await()
        val lastPublishToChannel01 = channel01.sendText("message02In$channelId01").await()
        channel02.sendText("message01In$channelId02").await()
        val lastPublishToChannel02 = channel02.sendText("message02In$channelId02").await()

        // register lister of "Receipt" event
        val assertionErrorInListener01 = CompletableDeferred<AssertionError?>()
        val removeListenerAndUnsubscribe01: AutoCloseable = chat.listenForEvents(
            type = EventContent.Receipt::class,
            channel = channelId01
        ) { event: Event<EventContent.Receipt> ->
            try {
                //we need to have try/catch here because assertion error will not cause test to fail
                assertEquals(channelId01, event.channelId)
                assertEquals(chat.currentUser.id, event.userId)
                assertNotEquals(lastReadMessageTimetokenValue, event.payload.messageTimetoken)
                assertEquals(lastPublishToChannel01.timetoken, event.payload.messageTimetoken)
                assertionErrorInListener01.complete(null)
            } catch (e: AssertionError) {
                assertionErrorInListener01.complete(e)
            }
        }
        val assertionErrorInListener02 = CompletableDeferred<AssertionError?>()
        val removeListenerAndUnsubscribe02 = chat.listenForEvents(
            type = EventContent.Receipt::class,
            channel = channelId02
        ) { event: Event<EventContent.Receipt> ->
            try {
                //we need to have try/catch here because assertion error will not cause test to fail
                assertEquals(channelId02, event.channelId)
                assertEquals(chat.currentUser.id, event.userId)
                assertNotEquals(lastReadMessageTimetokenValue, event.payload.messageTimetoken)
                assertEquals(lastPublishToChannel02.timetoken, event.payload.messageTimetoken)
                assertionErrorInListener02.complete(null)
            } catch (e: AssertionError) {
                assertionErrorInListener02.complete(e)
            }
        }

        // then
        val markAllMessageAsReadResponse: MarkAllMessageAsReadResponse = chat.markAllMessagesAsRead().await()

        // verify response contains updated "lastReadMessageTimetoken"
        markAllMessageAsReadResponse.memberships.forEach { membership: Membership ->
            //why membership.custom!!["lastReadMessageTimetoken"] returns double? <--this is default behaviour of GSON
            assertNotEquals(lastReadMessageTimetokenValue.toDouble(), membership.custom!!["lastReadMessageTimetoken"])
        }

        // verify each Membership has updated custom value for "lastReadMessageTimetoken"
        val userMembership: MembershipsResponse = chat.currentUser.getMemberships().await()
        userMembership.memberships.forEach { membership: Membership ->
            assertNotEquals(lastReadMessageTimetokenValue.toDouble(), membership.custom!!["lastReadMessageTimetoken"])
        }

        // verify assertion inside listeners
        assertionErrorInListener01.await()?.let { throw it }
        assertionErrorInListener02.await()?.let { throw it }

        //remove messages
        chat.pubNub.deleteMessages(listOf(channelId01, channelId02))

        // remove listeners and unsubscribe
        removeListenerAndUnsubscribe01.close()
        removeListenerAndUnsubscribe02.close()

        //remove memberships (user). This will be done in tearDown method
    }

    @Ignore // fails from time to time
    @Test
    fun can_getUnreadMessagesCount_onMembership() = runTest {
        val channelId01 = channel01.id

        // send message
        channel01.sendText("message01In$channelId01").await()
        withContext(Dispatchers.Default) {
            delay(150) // history calls have around 130ms of cache time
        }
        // join (implicitly setLastReadMessageTimetoken)
        val joinResult: JoinResult = channel01.join { }.await()
        val membership = joinResult.membership
        val unreadMessageCount: Long? = membership.getUnreadMessagesCount().await()
        assertEquals(0, unreadMessageCount)

        // send message
        channel01.sendText("message02In$channelId01").await()
        withContext(Dispatchers.Default) {
            delay(150) // history calls have around 130ms of cache time
        }
        val unreadMessageCount02: Long? = membership.getUnreadMessagesCount().await()
        assertEquals(1L, unreadMessageCount02)

        // markAllMessagesAsRead
        val markAllMessageAsReadResponse: MarkAllMessageAsReadResponse = chat.markAllMessagesAsRead().await()
        val membershipWithUpgradeLastReadMessageTimetoken = markAllMessageAsReadResponse.memberships.first()
        withContext(Dispatchers.Default) {
            delay(1050)
        }
        val unreadMessageCount03: Long? = membershipWithUpgradeLastReadMessageTimetoken.getUnreadMessagesCount().await()
        assertEquals(0, unreadMessageCount03)

        //remove messages
        chat.pubNub.deleteMessages(listOf(channelId01))
    }

    @Ignore // fails from time to time
    @Test
    fun can_getUnreadMessageCounts_global() = runTest {
        val channelId01 = channel01.id
        val channelId02 = channel02.id

        // join two channels
        channel01.join { }.await()
        channel02.join { }.await()

        // send message
        channel01.sendText("message01In$channelId01").await()
        channel02.sendText("message01In$channelId02").await()
        withContext(Dispatchers.Default) {
            delay(1500) // history calls have around 130ms of cache time
        }

        // read message count
        var unreadMessagesCounts = chat.getUnreadMessagesCounts().await()
        var unreadMessagesCountsForChannel01: Long =
            unreadMessagesCounts.find { unreadMessagesCount -> unreadMessagesCount.channel.id == channelId01 }?.count
                ?: 0
        var unreadMessagesCountsForChannel02: Long =
            unreadMessagesCounts.find { unreadMessagesCount -> unreadMessagesCount.channel.id == channelId02 }?.count
                ?: 0
        assertEquals(1, unreadMessagesCountsForChannel01)
        assertEquals(1, unreadMessagesCountsForChannel02)

        // markUnread
        chat.markAllMessagesAsRead().await()
        withContext(Dispatchers.Default) {
            delay(5000) // todo not sure why 5s is needed here but without it test doesn't pass in most cases. What can take so long? markAllMessagesAsRead method sets Membership. Does it take so long to propagate?
        }

        // read message count
        unreadMessagesCounts = chat.getUnreadMessagesCounts().await()
        unreadMessagesCountsForChannel01 =
            unreadMessagesCounts.find { unreadMessagesCount: GetUnreadMessagesCounts -> unreadMessagesCount.channel.id == channelId01 }?.count
                ?: 0
        unreadMessagesCountsForChannel02 =
            unreadMessagesCounts.find { unreadMessagesCount: GetUnreadMessagesCounts -> unreadMessagesCount.channel.id == channelId02 }?.count
                ?: 0
        assertEquals(0, unreadMessagesCountsForChannel01)
        assertEquals(0, unreadMessagesCountsForChannel02) //todo when run in set sometimes fails :/

        // remove messages
        chat.pubNub.deleteMessages(listOf(channelId01, channelId02))
    }

    @Test
    fun shouldReturnNoSuggestions_whenNoDataInCacheAndNoChannelsInChat() = runTest {
        val channelSuggestions: Set<Channel> = chat.getChannelSuggestions("sas#las").await()
        assertEquals(0, channelSuggestions.size)
    }

    @Test
    fun shouldReturnSuggestions_whenNoDataInCacheButChannelsAvailableInChat() = runTest {
        val channelId = randomString()
        val channelName = "channelName_$channelId"
        chat.createChannel(id = channelId, name = channelName).await()

        // then
        val channelSuggestions: Set<Channel> = chat.getChannelSuggestions("sas#$channelName").await()

        assertEquals(1, channelSuggestions.size)
        assertEquals(channelName, channelSuggestions.first().name)

        // cleanup
        chat.deleteChannel(id = channelId, soft = false).await()
    }
}
