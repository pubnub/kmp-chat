package com.pubnub.integration

import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.kmp.Membership
import com.pubnub.kmp.User
import com.pubnub.kmp.restrictions.GetRestrictionsResponse
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ChannelIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun join() = runTest {
        val channel = chat.createChannel(randomString()).await()

        val result = channel.join {}.await()

        assertEquals(config.userId.value, result.membership.user.id)
        assertEquals(channel.id, result.membership.channel.id)
    }

    @Test
    fun join_receivesMessages() = runTest {
    }

    @Test
    fun join_close_disconnects() = runTest {
    }

    @Test
    fun join_updates_lastReadMessageTimetoken() = runTest {
    }

    @Test
    fun connect() = runTest {
    }

    @Test
    fun connect_receivesMessages() = runTest {
    }

    @Test
    fun connect_close_disconnects() = runTest {
    }

    @Test
    fun getUserRestrictions() = runTest {
        val userId = "userId"
        val user = User(chat = chatPam, id = userId)
        val ban = true
        val mute = true
        val reason = "rude"
        val channelId = channelPam.id
        channelPam.setRestrictions(user = user, ban = ban, mute = mute, reason = reason).await()
        val restriction = channelPam.getUserRestrictions(user).await()

        assertEquals(userId, restriction.userId)
        assertEquals(channelId, restriction.channelId)
        assertEquals(ban, restriction.ban)
        assertEquals(mute, restriction.mute)
        assertEquals(reason, restriction.reason)
    }

    @Test
    fun getUsersRestrictions() = runTest {
        val userId01 = "userId01"
        val userId02 = "userId02"
        val ban = true
        val mute = true
        val reason = "rude"
        val limit = 3
        val page = null
        val sort: Collection<PNSortKey<PNMemberKey>> = listOf(PNSortKey.PNAsc(PNMemberKey.UUID_ID))
        val channelId = channelPam.id

        channelPam.setRestrictions(user = User(chat = chatPam, id = userId01), ban = ban, mute = mute, reason = reason)
            .await()
        channelPam.setRestrictions(user = User(chat = chatPam, id = userId02), ban = ban, mute = mute, reason = reason)
            .await()

        val getRestrictionsResponse: GetRestrictionsResponse =
            channelPam.getUsersRestrictions(limit = limit, page = page, sort = sort).await()

        assertEquals(2, getRestrictionsResponse.total)
        assertEquals(200, getRestrictionsResponse.status)
        val firstRestriction = getRestrictionsResponse.restrictions.first()
        assertEquals(channelId, firstRestriction.channelId)
        assertEquals(ban, firstRestriction.ban)
        assertEquals(mute, firstRestriction.mute)
        assertEquals(reason, firstRestriction.reason)
        val secondRestriction = getRestrictionsResponse.restrictions.elementAt(1)
        assertEquals(channelId, secondRestriction.channelId)
        assertEquals(ban, secondRestriction.ban)
        assertEquals(mute, secondRestriction.mute)
        assertEquals(reason, secondRestriction.reason)
    }

    @Test
    fun shouldReturnNoUserSuggestions_whenNoDatInCacheAndNoChannelsInChat() = runTest {
        val userSuggestions = channel01.getUserSuggestions("sas@las").await()
        assertEquals(0, userSuggestions.size)
    }

    @Test
    fun shouldReturnUserSuggestions_whenNoDataInCacheButUserAvailableInChat() = runTest {
        // given
        val userName = "userName_${someUser.id}"
        val user: User = chat.createUser(id = someUser.id, name = userName).await()
        channel01.invite(someUser).await()

        // when no data in cache
        val userSuggestionsMemberships: Set<Membership> = channel01.getUserSuggestions("sas@$userName").await()

        // then
        assertEquals(1, userSuggestionsMemberships.size)
        assertEquals(someUser.id, userSuggestionsMemberships.first().user.id)
        assertEquals(userName, userSuggestionsMemberships.first().user.name)

        // when data in cache
        val userSuggestionsMembershipsFromCache: Set<Membership> = channel01.getUserSuggestions("sas@$userName").await()

        // then
        assertEquals(1, userSuggestionsMembershipsFromCache.size)
        assertEquals(someUser.id, userSuggestionsMembershipsFromCache.first().user.id)
        assertEquals(userName, userSuggestionsMembershipsFromCache.first().user.name)
    }

    @Test
    fun streamReadReceipts() = runTest(timeout = 10.seconds) {
        val completableBeforeMark = CompletableDeferred<Unit>()
        val completableAfterMark = CompletableDeferred<Unit>()

        try {
            chat.deleteUser("user2", false).await()
        } finally {
        }
        val user2 = chat.createUser(User(chat, "user2")).await()

        val channel = chat.createDirectConversation(user2).await().channel
        channel.sendText("text1").await().timetoken
        chat.markAllMessagesAsRead().await()

        val tt = channel.sendText("text2").await().timetoken
        val dispose = channel.streamReadReceipts { receipts ->
            val lastRead = receipts.entries.find { it.value.contains(chat.currentUser.id) }?.key
            if (lastRead != null) {
                if (tt > lastRead) {
                    completableBeforeMark.complete(Unit) // before calling markAllMessagesRead
                } else {
                    completableAfterMark.complete(Unit) // after calling markAllMessagesRead
                }
            }
        }

        completableBeforeMark.await()
        chat.markAllMessagesAsRead().await()
        completableAfterMark.await()

        dispose.close()
    }

    @Test
    fun getMessage() = runTest(timeout = 10.seconds){
        val messageText = "some text"
        val channel = chat.createChannel(randomString()).await()
        val tt = channel.sendText(messageText, ttl = 60).await().timetoken

        delayInMillis(150)

        val message = channel.getMessage(tt).await()
        assertEquals(messageText, message?.text)
        assertEquals(tt, message?.timetoken)
    }
}
