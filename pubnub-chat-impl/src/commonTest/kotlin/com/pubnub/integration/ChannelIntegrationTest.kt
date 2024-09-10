package com.pubnub.integration

import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.chat.Channel
import com.pubnub.chat.Event
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.PINNED_MESSAGE_TIMETOKEN
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.types.JoinResult
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ChannelIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun getPinnedMessage() = runTest {
        val timetoken = channel01.sendText("Text text text").await()
        val message = channel01.getMessage(timetoken.timetoken).await()!!

        val updatedChannel = channel01.pinMessage(message).await()
        assertEquals(timetoken.timetoken.toString(), updatedChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN))
        val pinnedMessage = updatedChannel.getPinnedMessage().await()

        assertNotNull(pinnedMessage)
    }

    @Test
    fun join() = runTest {
        val channelId = randomString()
        val channel = chat.createChannel(channelId).await()

        val result = channel.join().await()

        assertEquals(config.userId.value, result.membership.user.id)
        assertEquals(channel.id, result.membership.channel.id)

        chat.deleteChannel(channelId).await()
    }

    @Test
    fun join_receivesMessages() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val messageText = randomString()
        val message = CompletableDeferred<Message>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            val joinResult = CompletableDeferred<JoinResult>()
            pubnub.awaitSubscribe(listOf(channel.id)) {
                channel.join { receivedMessage ->
                    message.complete(receivedMessage)
                }.async {
                    it.onSuccess {
                        joinResult.complete(it)
                    }.onFailure {
                        joinResult.completeExceptionally(it)
                    }
                }
            }
            val result = joinResult.await()
            channel.sendText(messageText).await()

            assertEquals(config.userId.value, result.membership.user.id)
            assertEquals(channel.id, result.membership.channel.id)
            assertEquals(messageText, message.await().text)
            result.disconnect?.close()
        }
    }

    @Test
    fun join_close_unsubscribes() = runTest {
        val channel = chat.createChannel(randomString()).await()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            val joinResult = CompletableDeferred<JoinResult>()
            pubnub.awaitSubscribe(channels = listOf(channel.id)) {
                channel.join { }.async {
                    it.onSuccess {
                        joinResult.complete(it)
                    }.onFailure {
                        joinResult.completeExceptionally(it)
                    }
                }
            }
            val result = joinResult.await()
            pubnub.awaitUnsubscribe(channels = listOf(channel.id)) {
                result.disconnect?.close()
            }
        }
    }

    @Test
    fun join_updates_lastReadMessageTimetoken() = runTest {
        val then = Instant.fromEpochSeconds(chat.pubNub.time().await().timetoken / 10000000)
        val channel = chat.createChannel(randomString()).await()

        val lastReadMessage: Long = channel.join().await().membership.lastReadMessageTimetoken ?: 0

        assertTrue(lastReadMessage > 0)
        assertContains(then..Clock.System.now(), Instant.fromEpochSeconds(lastReadMessage / 10000000))
    }

    @Test
    fun connect() = runTest {
        val channel = chat.createChannel(randomString()).await()

        val result = channel.connect {}
        result.close()
    }

    @Test
    fun connect_receivesMessages() = runTest {
        val channel = chat.createChannel(randomString()).await()
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
            assertEquals(messageText, message.await().text)
            assertFalse(channel.getMembers().await().members.any { it.user.id == chat.currentUser.id })
            unsubscribe?.close()
        }
    }

    @Test
    fun connect_close_unsubscribes() = runTest {
        val channel = chat.createChannel(randomString()).await()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            val disconnect = CompletableDeferred<AutoCloseable>()
            pubnub.awaitSubscribe(channels = listOf(channel.id)) {
                disconnect.complete(channel.connect { })
            }
            val closeable = disconnect.await()
            pubnub.awaitUnsubscribe(channels = listOf(channel.id)) {
                closeable.close()
            }
        }
    }

    @Test
    fun getUserRestrictions() = runTest {
        val userId = "userId"
        val user = UserImpl(chat = chatPam, id = userId)
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

        channelPam.setRestrictions(
            user = UserImpl(chat = chatPam, id = userId01),
            ban = ban,
            mute = mute,
            reason = reason
        )
            .await()
        channelPam.setRestrictions(
            user = UserImpl(chat = chatPam, id = userId02),
            ban = ban,
            mute = mute,
            reason = reason
        )
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

    // todo flaky
    @Test
    fun streamReadReceipts() = runTest(timeout = 10.seconds) {
        val completableBeforeMark = CompletableDeferred<Unit>()
        val completableAfterMark = CompletableDeferred<Unit>()

        try {
            chat.deleteUser("user2", false).await()
        } catch (_: Exception) {
        }
        val user2 = chat.createUser(UserImpl(chat, "user2")).await()

        val channel = chat.createDirectConversation(user2).await().channel
        channel.sendText("text1").await().timetoken
        chat.markAllMessagesAsRead().await()

        val tt = channel.sendText("text2").await().timetoken
        var dispose: AutoCloseable? = null
        pubnub.test(backgroundScope, checkAllEvents = false) {
            pubnub.awaitSubscribe(listOf(channel.id)) {
                dispose = channel.streamReadReceipts { receipts ->
                    val lastRead = receipts.entries.find { it.value.contains(chat.currentUser.id) }?.key
                    if (lastRead != null) {
                        if (tt > lastRead) {
                            completableBeforeMark.complete(Unit) // before calling markAllMessagesRead
                        } else {
                            completableAfterMark.complete(Unit) // after calling markAllMessagesRead
                        }
                    }
                }
            }

            completableBeforeMark.await()
            chat.markAllMessagesAsRead().await()
            completableAfterMark.await()

            dispose?.close()
        }
    }

    @Test
    fun streamUpdatesOn() = runTest {
        val newName = "newName"
        chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let {
                createCustomObject(it)
            },
            channel01.type,
            channel01.status
        ).await()
        chat.createChannel(
            channel02.id,
            channel02.name,
            channel02.description,
            channel02.custom?.let {
                createCustomObject(it)
            },
            channel02.type,
            channel02.status
        ).await()
        delayInMillis(1000)

        val expectedUpdates = listOf<List<Channel>>(
            listOf(
                channel01.asImpl().copy(name = newName),
                channel02.asImpl()
            ).sortedBy {
                it.id
            },
            listOf(
                channel01.asImpl().copy(name = newName),
                channel02.asImpl().copy(description = newName)
            ).sortedBy {
                it.id
            },
            listOf(
                channel02.asImpl().copy(description = newName)
            ).sortedBy {
                it.id
            },
            emptyList()
        )
        val actualUpdates = mutableListOf<List<Channel>>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id, channel02.id)) {
                dispose = BaseChannel.streamUpdatesOn(listOf(channel01, channel02)) { channels ->
                    actualUpdates.add(channels.map { it.asImpl().copy(updated = null) }.sortedBy { it.id })
                }
            }

            channel01.update(name = newName).await()

            channel02.update(description = newName).await()

            channel01.delete().await()

            channel02.delete().await()

            delayInMillis(1000)
            dispose?.close()
        }

        assertEquals(expectedUpdates.map { it.map { it.asImpl().copy(updated = null) as Channel } }, actualUpdates)
    }

    @Test
    fun getMessage() = runTest(timeout = 200.seconds) {
        val messageText = "some text"
        val tt = channel01.sendText(text = messageText, ttl = 60).await().timetoken

        delayInMillis(150)

        val message = channel01.getMessage(tt).await()
        assertEquals(messageText, message?.text)
        assertEquals(tt, message?.timetoken)
    }

    @Test
    fun getMessageWithMentionedUsersAndReferencedChannels() = runTest(timeout = 200.seconds) {
        val messageText = "some text"
        val mentionedPosition = 1
        val referencedPosition = 1
        val mentionedUsers =
            mapOf<Int, MessageMentionedUser>(mentionedPosition to MessageMentionedUser("user01Id", "user01Name"))
        val referencedChannels =
            mapOf<Int, MessageReferencedChannel>(referencedPosition to MessageReferencedChannel(id = "channel01Id", name = "channel01Name"))
        val tt = channel01.sendText(
            text = messageText,
            ttl = 60,
            mentionedUsers = mentionedUsers,
            referencedChannels = referencedChannels
        ).await().timetoken

        delayInMillis(150)

        val message = channel01.getMessage(tt).await()
        val actualMentionedUsers: Map<Int, MessageMentionedUser>? = message?.mentionedUsers
        val actualMentionPosition = actualMentionedUsers?.keys?.first()
        assertIs<Int>(actualMentionPosition)
        assertEquals(mentionedPosition, actualMentionPosition)
        val actualReferencedChannels: Map<Int, MessageReferencedChannel>? = message?.referencedChannels
        val actualReferencedPosition = actualReferencedChannels?.keys?.first()
        assertIs<Int>(actualReferencedPosition)
        assertEquals(referencedPosition, actualReferencedPosition)
    }

    @Test
    fun getTyping() = runTest(timeout = 10.seconds) {
        val channelId = randomString()
        val channel = chat.createChannel(channelId).await()
        val typingStarted = CompletableDeferred<Unit>()
        val typingStopped = CompletableDeferred<Unit>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                dispose = channel.getTyping {
                    if (it.contains(chat.currentUser.id)) {
                        typingStarted.complete(Unit)
                    } else {
                        if (typingStarted.isCompleted) {
                            typingStopped.complete(Unit)
                        } else {
                            typingStopped.completeExceptionally(Throwable("Stopped before started"))
                        }
                    }
                }
            }

            channel.startTyping().await()
            typingStarted.await()
            channel.stopTyping().await()
            typingStopped.await()

            dispose?.close()
        }

        chat.deleteChannel(channelId).await()
    }

    @Test
    fun can_getMessageReportsHistory() = runTest {
        val pnPublishResult = channel01.sendText(text = "message1").await()
        val timetoken = pnPublishResult.timetoken
        val message = channel01.getMessage(timetoken).await()!!

        // report messages
        val reason01 = "rude"
        val reason02 = "too verbose"
        message.report(reason01).await()
        message.report(reason02).await()

        // getMessageReport
        val eventsHistoryResult: GetEventsHistoryResult = channel01.getMessageReportsHistory().await()
        assertEquals(2, eventsHistoryResult.events.size)

        // then
        assertNotNull(
            eventsHistoryResult.events.find { event: Event<EventContent> ->
                val payload = event.payload as EventContent.Report
                payload.reason == reason01
            }
        )
        assertNotNull(
            eventsHistoryResult.events.find { event: Event<EventContent> ->
                val payload = event.payload as EventContent.Report
                payload.reason == reason02
            }
        )
    }

    @Test
    fun can_streamMessageReports() = runTest {
        val numberOfReports = atomic(0)
        val reason01 = "rude"
        val reason02 = "too verbose"
        val messageText = "message1"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val timetoken = pnPublishResult.timetoken
        val message = channel01.getMessage(timetoken).await()!!
        val assertionErrorInCallback = CompletableDeferred<AssertionError?>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var streamMessageReportsCloseable: AutoCloseable? = null

            pubnub.awaitSubscribe(listOf("PUBNUB_INTERNAL_MODERATION_${channel01.id}")) {
                streamMessageReportsCloseable = channel01.streamMessageReports { reportEvent: Event<EventContent.Report> ->
                    try {
                        // we need to have try/catch here because assertion error will not cause test to fail
                        numberOfReports.incrementAndGet()
                        val reportReason = reportEvent.payload.reason
                        assertTrue(reportReason == reason01 || reportReason == reason02)
                        assertEquals(messageText, reportEvent.payload.text)
                        assertTrue(reportEvent.payload.reportedMessageChannelId?.contains(INTERNAL_MODERATION_PREFIX)!!)
                        assertTrue(reportEvent.channelId.contains(INTERNAL_MODERATION_PREFIX))
                        if (numberOfReports.value == 2) {
                            assertionErrorInCallback.complete(null)
                        }
                    } catch (e: AssertionError) {
                        assertionErrorInCallback.complete(e)
                    }
                }
            }

            // report messages
            message.report(reason01).await()
            message.report(reason02).await()

            assertionErrorInCallback.await()?.let { assertionError -> throw (assertionError) }
            assertEquals(2, numberOfReports.value)

            streamMessageReportsCloseable?.close()
        }
    }
}

private fun Channel.asImpl(): ChannelImpl {
    return this as ChannelImpl
}
