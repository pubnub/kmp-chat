package com.pubnub.integration

import com.pubnub.api.models.consumer.pubsub.PNEvent
import com.pubnub.chat.Event
import com.pubnub.chat.Message
import com.pubnub.chat.internal.PREFIX_PUBNUB_PRIVATE
import com.pubnub.chat.internal.mutelist.MutedUsersImpl
import com.pubnub.chat.listenForEvents
import com.pubnub.chat.mutelist.MutedUsers
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import delayForHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutedUsersIntegrationTest : BaseChatIntegrationTest() {
    private fun getMutedUsers(sync: Boolean = false): MutedUsers = MutedUsersImpl(pubnub, pubnub.configuration.userId.value, sync)

    @Test
    fun muteUser_adds_user_to_set() {
        val mutedUsers = getMutedUsers()

        mutedUsers.muteUser(someUser.id)

        assertContains(mutedUsers.muteSet, someUser.id)

        mutedUsers.muteUser(someUser02.id)

        assertContains(mutedUsers.muteSet, someUser.id)
        assertContains(mutedUsers.muteSet, someUser02.id)
    }

    @Test
    fun unmuteUser_removes_user_from_set() {
        val mutedUsers = getMutedUsers()
        mutedUsers.muteUser(someUser.id)
        mutedUsers.muteUser(someUser02.id)

        mutedUsers.unmuteUser(someUser.id)

        assertContains(mutedUsers.muteSet, someUser02.id)
        assertFalse { mutedUsers.muteSet.contains(someUser.id) }

        mutedUsers.unmuteUser(someUser02.id)

        assertTrue { mutedUsers.muteSet.isEmpty() }
    }

    @Test
    fun sync_updates_between_clients() = runTest {
        val mutedUsers1 = getMutedUsers(true)
        val mutedUsers2 = MutedUsersImpl(pubnub02, pubnub.configuration.userId.value, true)
        pubnub.test(backgroundScope) {
            pubnub.awaitSubscribe(listOf("aaa")) // just to kick off connection
            pubnub.awaitSubscribe(listOf("${PREFIX_PUBNUB_PRIVATE}${pubnub.configuration.userId.value}.mute1")) {
                // custom subscription block empty, let mutedUsers subscribe for us
            }
            mutedUsers2.muteUser(someUser02.id).await()
            nextEvent<PNEvent>()
            assertContains(mutedUsers1.muteSet, someUser02.id)
        }
    }

    @Test
    fun connect_filters_muted_users() = runTest {
        val channel = chat.createChannel(randomString()).await()
        chat.mutedUsers.muteUser(chat.currentUser.id)
        val messageText = randomString()
        val message = CompletableDeferred<Message>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                unsubscribe = channel.connect {
                    message.complete(it)
                }
            }
            channel.sendText(messageText).await()
            chat02.getChannel(channel.id).await()?.sendText("text")?.await()
            assertEquals(chat02.currentUser.id, message.await().userId)
            unsubscribe?.close()
        }
    }

    @Test
    fun getHistory_filters_muted_users() = runTest {
        val channel = chat.createChannel(randomString()).await()
        chat.mutedUsers.muteUser(chat.currentUser.id)
        val messageText = randomString()
        val tt1 = channel.sendText(messageText).await()
        val tt2 = chat02.getChannel(channel.id).await()!!.sendText("text").await()
        delayForHistory()
        val historyResponse = channel.getHistory(tt2.timetoken + 1, tt1.timetoken).await()
        assertEquals(1, historyResponse.messages.size)
        assertEquals(chat02.currentUser.id, historyResponse.messages.first().userId)
    }

    @Test
    fun getEventsHistory_filters_muted_users() = runTest {
        // given
        val startTimetoken = pubnub.time().await().timetoken
        chat.mutedUsers.muteUser(chat02.currentUser.id)
        channel01Chat02.invite(chat.currentUser).await()
        channel01Chat02.sendText(
            text = "message01In${channel01.id}",
            usersToMention = listOf(chat.currentUser.id)
        ).await()

        // when
        delayForHistory()
        delayForHistory()
        val eventsForUser: GetEventsHistoryResult = chat.getEventsHistory(
            channelId = chat.currentUser.id,
            endTimetoken = startTimetoken
        ).await()

        // then
        assertTrue { eventsForUser.events.isEmpty() }
    }

    @Test
    fun listenForEvents_filters_muted_users() = runTest {
        // given
        chat.mutedUsers.muteUser(chat02.currentUser.id)
        val completableInvite = CompletableDeferred<Event<EventContent.Invite>>()
        chat.listenForEvents(chat.currentUser.id) { invite: Event<EventContent.Invite> ->
            completableInvite.complete(invite)
        }

        // when
        channel01Chat02.invite(chat.currentUser).await()
        delayForHistory()

        // then
        assertFalse { completableInvite.isCompleted }
    }
}
