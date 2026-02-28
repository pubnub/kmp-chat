package com.pubnub.integration

import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.utils.Clock
import com.pubnub.api.utils.Instant
import com.pubnub.chat.Channel
import com.pubnub.chat.Event
import com.pubnub.chat.MentionTarget
import com.pubnub.chat.Message
import com.pubnub.chat.MessageElement
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.internal.PINNED_MESSAGE_TIMETOKEN
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.listeners.ConnectionStatus
import com.pubnub.chat.listeners.ConnectionStatusCategory
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.ReadReceipt
import com.pubnub.chat.types.Report
import com.pubnub.internal.PLATFORM
import com.pubnub.kmp.Uploadable
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import delayForHistory
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ChannelIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun getPinnedMessage() = runTest {
        val timetoken = channel01.sendText("Text text text").await()
        delayForHistory()
        val message = channel01.getMessage(timetoken.timetoken).await()!!

        val updatedChannel = channel01.pinMessage(message).await()
        assertEquals(timetoken.timetoken.toString(), updatedChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN))
        delayForHistory()
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

        result.disconnect?.close()
        chat.deleteChannel(channelId).await()
    }

    @Test
    fun can_onMessageReceived() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val messageText = randomString()
        val message = CompletableDeferred<Message>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var onMessageReceivedResult: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                onMessageReceivedResult = channel.onMessageReceived { receivedMessage ->
                    message.complete(receivedMessage)
                }
            }
            channel.sendText(messageText).await()

            assertEquals(messageText, message.await().text)

            pubnub.awaitUnsubscribe(channels = listOf(channel.id)) {
                onMessageReceivedResult?.close()
            }
        }
    }

    @Test
    fun can_join() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val status = "myStatus"
        val type = "myType"
        val messageText = randomString()
        val message = CompletableDeferred<Message>()

        val membership = channel.joinChannel(status = status, type = type).await()

        assertEquals(config.userId.value, membership.user.id)
        assertEquals(channel.id, membership.channel.id)
        assertEquals(status, membership.status)
        assertEquals(type, membership.type)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun streamPresence() = runTest {
        val completable = CompletableDeferred<Collection<String>>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var closeable: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                closeable = channel01.onPresenceChanged {
                    if (someUser02.id in it) {
                        completable.complete(it)
                    }
                }
            }

            val closeable2 = channel01Chat02.connect {}
            completable.await()
            closeable?.close()
            closeable2.close()
        }
    }

    @Test
    fun join_updates_lastReadMessageTimetoken() = runTest {
        val then = Instant.fromEpochSeconds(chat.pubNub.time().await().timetoken / 10000000, 0)
        val channel = chat.createChannel(randomString()).await()

        val lastReadMessage: Long = channel.join().await().membership.lastReadMessageTimetoken ?: 0

        assertTrue(lastReadMessage > 0)
        assertContains(then..Clock.System.now(), Instant.fromEpochSeconds(lastReadMessage / 10000000, 0))
    }

    @Test
    fun onMessageReceived() = runTest {
        val channel = chat.createChannel(randomString()).await()

        val result = channel.onMessageReceived {}
        result.close()
    }

    @Test
    fun onMessageReceived_receivesMessages() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val messageText = randomString()
        val message = CompletableDeferred<Message>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                unsubscribe = channel.onMessageReceived {
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
    fun onMessageReceived_close_unsubscribes() = runTest {
        val channel = chat.createChannel(randomString()).await()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            val disconnect = CompletableDeferred<AutoCloseable>()
            pubnub.awaitSubscribe(channels = listOf(channel.id)) {
                disconnect.complete(channel.onMessageReceived { })
            }
            val closeable = disconnect.await()
            pubnub.awaitUnsubscribe(channels = listOf(channel.id)) {
                closeable.close()
            }
        }
    }

    @Test
    fun getUserRestrictions() = runTest {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }
        val userId = "userId"
        val user = UserImpl(chat = chatPamServer, id = userId)
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
    fun muteAndUnMuteUser() = runTest {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }
        var mute = true
        val reason = "rude"

        channelPam.setRestrictions(user = userPamServer, mute = mute, reason = reason).await()
        var restriction = channelPam.getUserRestrictions(userPamServer).await()
        assertEquals(mute, restriction.mute)

        mute = false
        channelPam.setRestrictions(user = userPamServer, mute = mute, reason = reason).await()
        restriction = channelPam.getUserRestrictions(userPamServer).await()
        assertEquals(mute, restriction.mute)
        assertFalse(restriction.ban)
        assertNull(restriction.reason)
    }

    @Test
    fun canGetRestrictionForUserThatDoesNotHaveRestrictionSet() = runTest {
        val restriction = channel01.getUserRestrictions(userPamServer).await()
        assertEquals(channel01.id, restriction.channelId)
        assertEquals(userPamServer.id, restriction.userId)
        assertFalse(restriction.mute)
        assertFalse(restriction.ban)
        assertNull(restriction.reason)
    }

    @Test
    fun getUsersRestrictions() = runTest {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }
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
            user = UserImpl(chat = chatPamServer, id = userId01),
            ban = ban,
            mute = mute,
            reason = reason
        )
            .await()
        channelPam.setRestrictions(
            user = UserImpl(chat = chatPamServer, id = userId02),
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
        val userSuggestions = channel01.getUserSuggestions("las").await()
        assertEquals(0, userSuggestions.size)
    }

    @Test
    fun onTyping_listener_should_not_receive_typing_changed_info_after_close() = runTest {
        val numberOfTypingEvents = atomic(0)
        pubnub02.test(backgroundScope, checkAllEvents = false) {
            var typingSubscription: AutoCloseable? = null
            pubnub02.awaitSubscribe(listOf(channel01.id)) {
                typingSubscription = channel01Chat02.onTypingChanged { typingUserIds ->
                    numberOfTypingEvents.incrementAndGet()
                }
            }
            // T = 0s: User1 starts typing
//            println("-=T ${Clock.System.now()} = 0s: user: ${channel01.chat.currentUser.id} starts typing")
            channel01.startTyping().await()
            delayInMillis(1000)

            pubnub02.awaitUnsubscribe(listOf(channel01.id)) {
                typingSubscription?.close()
            }
            channel01Chat02.startTyping().await()
            delayInMillis(1000)
            channel01.startTyping().await()
            delayInMillis(1000)
            assertEquals(1, numberOfTypingEvents.value)
        }
    }

    @Test
    fun shouldReturnUserSuggestions_whenNoDataInCacheButUserAvailableInChat() = runTest {
        // given
        val userName = "userName_${someUser.id}"
        val user = chat.createUser(id = someUser.id, name = userName).await()
        channel01.invite(someUser).await()

        // when no data in cache
        val userSuggestionsMemberships = channel01.getUserSuggestions(userName).await()

        // then
        assertEquals(1, userSuggestionsMemberships.size)
        assertEquals(someUser.id, userSuggestionsMemberships.first().user.id)
        assertEquals(userName, userSuggestionsMemberships.first().user.name)

        // when data in cache
        val userSuggestionsMembershipsFromCache = channel01.getUserSuggestions(userName).await()

        // then
        assertEquals(1, userSuggestionsMembershipsFromCache.size)
        assertEquals(someUser.id, userSuggestionsMembershipsFromCache.first().user.id)
        assertEquals(userName, userSuggestionsMembershipsFromCache.first().user.name)
    }

    @Test
    fun canGetSoftDeletedUsers() = runTest {
        val userId = "user2"
        try {
            chat.deleteUser(userId, false).await()
        } catch (_: Exception) {
        }
        chat.createUser(UserImpl(chat, userId)).await()
        chat.deleteUser(id = userId, soft = true).await()

        val getUsersResponse = chat.getUsers(filter = "id == '$userId' && status=='deleted'").await()

        assertEquals(userId, getUsersResponse.users.first().id)
        assertEquals("deleted", getUsersResponse.users.first().status)

        // clean
        try {
            chat.deleteUser(userId, false).await()
        } catch (_: Exception) {
        }
    }

    // todo flaky
    @Ignore
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
        delayForHistory()
        chat.markAllMessagesAsRead().await()

        val tt = channel.sendText("text2").await().timetoken
        var dispose: AutoCloseable? = null
        pubnub.test(backgroundScope, checkAllEvents = false) {
            pubnub.awaitSubscribe(listOf(channel.id)) {
                dispose = channel.onReadReceiptReceived { receipt: ReadReceipt ->
                    if (receipt.userId == chat.currentUser.id) {
                        if (tt > receipt.lastReadTimetoken) {
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

        delayForHistory()
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
            mapOf<Int, MessageReferencedChannel>(
                referencedPosition to MessageReferencedChannel(
                    id = "channel01Id",
                    name = "channel01Name"
                )
            )
        val tt = channel01.sendText(
            text = messageText,
            ttl = 60,
            mentionedUsers = mentionedUsers,
            referencedChannels = referencedChannels
        ).await().timetoken

        delayForHistory()
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

    @Ignore // run it to see typing behaviour
    @Test
    fun can_illustrate_typing_behaviour() = runTest {
        val typingSubscription = channel01Chat02.onTypingChanged { typingUserIds: Collection<String> ->
            if (typingUserIds.isNotEmpty()) {
                println("-= ${Clock.System.now()} Users currently typing: ${typingUserIds.joinToString(", ")}")
            } else {
                println("-= ${Clock.System.now()} No one is typing now.")
            }
        }

        // T = 0s: User1 starts typing
        println("-=T ${Clock.System.now()} = 0s: user: ${channel01.chat.currentUser.id} starts typing")
        channel01.startTyping().await()
        delayInMillis(1000)

        // T = 1s: User2 starts typing
        println("-=T ${Clock.System.now()} = 1s: user: ${channel01Chat02.chat.currentUser.id} starts typing")
        channel01Chat02.startTyping().await()

        // Wait 6 seconds (T = 7s) for timeout to expire
        delayInMillis(6000)
        println("-=T ${Clock.System.now()} = 7s: user: ${channel01.chat.currentUser.id} starts typing again")
        channel01.startTyping().await()

        // Wait 2 more seconds (T = 9s)
        delayInMillis(2000)
        println("-=T ${Clock.System.now()} = 9s: user: ${channel01.chat.currentUser.id} stops typing")
        channel01.stopTyping().await()

        // Wait 4 seconds for typing to timeout (T = 8s)
        delayInMillis(4000)
        println("-=T ${Clock.System.now()} = 13s: Timeout expires")

        // Close typing subscription
        typingSubscription.close()
    }

    @Test
    fun onTypingChanged() = runTest(timeout = 10.seconds) {
        val channelId = randomString() // change to channel
        val channel = chat.createChannel(channelId).await()
        val typingStarted = CompletableDeferred<Unit>()
        val typingStopped = CompletableDeferred<Unit>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                dispose = channel.onTypingChanged {
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
        delayForHistory()
        val message = channel01.getMessage(timetoken).await()!!

        // report messages
        val reason01 = "rude"
        val reason02 = "too verbose"
        message.report(reason01).await()
        message.report(reason02).await()

        // getMessageReport
        delayForHistory()
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
        delayForHistory()
        val message = channel01.getMessage(timetoken).await()!!
        val assertionErrorInCallback = CompletableDeferred<AssertionError?>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var streamMessageReportsCloseable: AutoCloseable? = null

            pubnub.awaitSubscribe(listOf("PUBNUB_INTERNAL_MODERATION_${channel01.id}")) {
                streamMessageReportsCloseable =
                    channel01.streamMessageReports { reportEvent: Event<EventContent.Report> ->
                        try {
                            // we need to have try/catch here because assertion error will not cause test to fail
                            numberOfReports.incrementAndGet()
                            val reportReason = reportEvent.payload.reason
                            assertTrue(reportReason == reason01 || reportReason == reason02)
                            assertEquals(messageText, reportEvent.payload.text)
                            assertEquals(message.channelId, reportEvent.payload.reportedMessageChannelId)
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

    @Test
    fun onMessageReported() = runTest {
        val numberOfReports = atomic(0)
        val reason01 = "rude"
        val reason02 = "too verbose"
        val messageText = "message1"
        val pnPublishResult = channel01.sendText(text = messageText).await()
        val timetoken = pnPublishResult.timetoken
        delayForHistory()
        val message = channel01.getMessage(timetoken).await()!!
        val assertionErrorInCallback = CompletableDeferred<AssertionError?>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var onMessageReportedCloseable: AutoCloseable? = null

            pubnub.awaitSubscribe(listOf("PUBNUB_INTERNAL_MODERATION_${channel01.id}")) {
                onMessageReportedCloseable =
                    channel01.onMessageReported { report: Report ->
                        try {
                            numberOfReports.incrementAndGet()
                            val reportReason = report.reason
                            assertTrue(reportReason == reason01 || reportReason == reason02)
                            assertEquals(messageText, report.text)
                            assertEquals(message.channelId, report.reportedMessageChannelId)
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

            onMessageReportedCloseable?.close()
        }
    }

    @Test
    fun canGetUpdatesOnChannel() = runTest {
        val expectedDescription = "Modified description"
        val expectedStatus = "ModifiedStatus"
        chat.createChannel(channel01.id).await()
        val completableDescription = CompletableDeferred<String?>()
        val completableStatus = CompletableDeferred<String?>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                dispose = channel01.streamUpdates { channel: Channel? ->
                    completableDescription.complete(channel?.description)
                    completableStatus.complete(channel?.status)
                }
            }
            channel01.update(description = expectedDescription, status = expectedStatus).await()
            assertEquals(expectedDescription, completableDescription.await())
            assertEquals(expectedStatus, completableStatus.await())

            dispose?.close()
        }
    }

    @Test
    fun onUpdated() = runTest {
        val expectedDescription = "Modified description"
        val expectedStatus = "ModifiedStatus"
        chat.createChannel(channel01.id).await()
        val completableDescription = CompletableDeferred<String?>()
        val completableStatus = CompletableDeferred<String?>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                dispose = channel01.onUpdated { channel: Channel ->
                    completableDescription.complete(channel.description)
                    completableStatus.complete(channel.status)
                }
            }
            channel01.update(description = expectedDescription, status = expectedStatus).await()
            assertEquals(expectedDescription, completableDescription.await())
            assertEquals(expectedStatus, completableStatus.await())

            dispose?.close()
        }
    }

    @Test
    fun onDeleted() = runTest {
        chat.createChannel(channel01.id).await()
        val completableDeleted = CompletableDeferred<Unit>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                dispose = channel01.onDeleted {
                    completableDeleted.complete(Unit)
                }
            }
            channel01.delete().await()
            completableDeleted.await()

            dispose?.close()
        }
    }

    @Test
    fun canCheck_whoIsPresent() = runTest {
        val channel01withChat = channel01
        val join01 = channel01withChat.onMessageReceived { }
        val join02 = channel01Chat02.onMessageReceived { }
        delayInMillis(1500)
        val whoIsPresent01: Collection<String> = channel01withChat.whoIsPresent().await()
        val whoIsPresent02: Collection<String> = channel01Chat02.whoIsPresent().await()

        join01.close()
        join02.close()

        assertEquals(whoIsPresent01.size, whoIsPresent02.size)
        assertTrue(whoIsPresent01.contains(someUser.id))
        assertTrue(whoIsPresent01.contains(someUser02.id))
        assertTrue(whoIsPresent02.contains(someUser.id))
        assertTrue(whoIsPresent02.contains(someUser02.id))
    }

    @Test
    fun canCheck_isPresent() = runTest {
        val channel01withChat = channel01
        val join01 = channel01withChat.onMessageReceived { }
        val join02 = channel01Chat02.onMessageReceived { }
        delayInMillis(1000)

        assertTrue(channel01withChat.isPresent(channel01Chat02.chat.currentUser.id).await())
        assertTrue(channel01Chat02.isPresent(channel01withChat.chat.currentUser.id).await())

        join01.close()
        join02.close()
    }

    @Test
    fun test_downloading_image() = runTest {
        if (PLATFORM == "iOS") { // todo enable for iOS
            return@runTest
        }
        val fileName = "TestImage01.png"
        val type = "image/png"
        val data: Uploadable = generateFileContentFromImage()
        val file = InputFile(name = fileName, type = type, source = data)

        // Upload file
        channel01.sendText(text = "messageWithImage", files = listOf(file)).await()

        delayInMillis(3000) // Wait for file to be processed

        // Get file metadata
        val filesResult = channel01.getFiles().await()
        assertTrue(filesResult.files.isNotEmpty())
        val uploadedFile = filesResult.files.first { it.name == fileName }
        assertEquals(fileName, uploadedFile.name)
        assertNotNull(uploadedFile.url)

        println("Image File URL: ${uploadedFile.url}")

        // Clean up
        channel01.deleteFile(uploadedFile.id, uploadedFile.name).await()
    }

    @Test
    fun test_downloading_file_via_history() = runTest {
        if (PLATFORM == "iOS") { // todo enable for iOS
            return@runTest
        }
        val fileName = "test_download_history.txt"
        val type = "text/plain"
        val data = generateFileContent(fileName = fileName)
        val file = InputFile(name = fileName, type = type, source = data)

        // Upload file and get timetoken
        val publishResult = channel01.sendText(text = "messageWithFile", files = listOf(file)).await()
        val sentTimetoken = publishResult.timetoken

        // Get message via history
        val history =
            channel01.getHistory(startTimetoken = sentTimetoken + 1, endTimetoken = sentTimetoken, count = 1).await()
        assertTrue(history.messages.isNotEmpty())
        val message = history.messages.first()
        assertEquals(1, message.files.size)
        val first = message.content.files?.first()
        println(first)
        val uploadedFile = message.files.first()
        assertEquals(fileName, uploadedFile.name)
        assertNotNull(uploadedFile.url)
        println("File URL from history: ${uploadedFile.url}")

        // Clean up
        channel01.deleteFile(uploadedFile.id, uploadedFile.name).await()
    }

    @Test
    fun messageDraft_send() = runTest {
        val draft = MessageDraftImpl(channel01, isTypingIndicatorTriggered = false)
        draft.update("Some text with a mention")
        draft.addMention(17, 7, MentionTarget.User("someUser"))
        val message = CompletableDeferred<Message>()
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                unsubscribe = channel01.onMessageReceived {
                    if (!message.isCompleted) {
                        message.complete(it)
                        unsubscribe?.close()
                    }
                }
            }
            draft.send().await()
            val elements = MessageDraftImpl.getMessageElements(message.await().text)

            assertEquals(
                listOf(
                    MessageElement.PlainText("Some text with a "),
                    MessageElement.Link("mention", MentionTarget.User("someUser"))
                ),
                elements
            )
        }
    }

    @Test
    fun canAddAndRemoveStatusListenerAndReceiveStatuses() = runTest {
        val statusReceivedOnline = CompletableDeferred<ConnectionStatusCategory>()
        val statusReceivedOffline = CompletableDeferred<ConnectionStatusCategory>()

        val connectionStatusListener = { status: ConnectionStatus ->
            when (status.category) {
                ConnectionStatusCategory.PN_CONNECTION_ONLINE -> {
                    if (!statusReceivedOnline.isCompleted) {
                        statusReceivedOnline.complete(status.category)
                    }
                }

                ConnectionStatusCategory.PN_CONNECTION_OFFLINE -> {
                    if (!statusReceivedOffline.isCompleted) {
                        statusReceivedOffline.complete(status.category)
                    }
                }

                else -> {
                    // do nothing for other statuses
                }
            }
            Unit
        }

        val statusListener: AutoCloseable = chat.addConnectionStatusListener(connectionStatusListener)
        delayInMillis(300)

        // we need to call connect to start EE that result triggering the online status
        val connect = channel01.onMessageReceived {
            // no need to handle messages in this test
        }

        // Wait for online status first
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_ONLINE, statusReceivedOnline.await())

        // Then disconnect and wait for offline status
        chat.disconnectSubscriptions().await()

        // Wait for offline status before removing the listener
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_OFFLINE, statusReceivedOffline.await())

        statusListener.close()
    }

    @Test
    fun canAddAndRemoveStatusListenerAndReceiveStatusesForReconnectAndDisconnect() = runTest {
        val firstStatusReceivedOnline = CompletableDeferred<ConnectionStatusCategory>()
        val secondStatusReceivedOnline = CompletableDeferred<ConnectionStatusCategory>()
        val statusReceivedOffline = CompletableDeferred<ConnectionStatusCategory>()
        var onlineStatusCount = 0

        val connectionStatusListener = { status: ConnectionStatus ->
            when (status.category) {
                ConnectionStatusCategory.PN_CONNECTION_ONLINE -> {
                    println("-=Received online status PN_CONNECTION_ONLINE: ${status.category}")
                    onlineStatusCount++
                    when (onlineStatusCount) {
                        1 -> firstStatusReceivedOnline.complete(status.category)
                        2 -> secondStatusReceivedOnline.complete(status.category)
                    }
                }

                ConnectionStatusCategory.PN_CONNECTION_OFFLINE -> {
                    println("-=Received online status PN_CONNECTION_OFFLINE: ${status.category}")
                    statusReceivedOffline.complete(status.category)
                }

                else -> {
                    // do nothing for other statuses
                }
            }
            Unit
        }

        val statusListener: AutoCloseable = chat.addConnectionStatusListener(connectionStatusListener)

        // we need to call connect to start EE that result triggering the online status
        val connect = channel01.onMessageReceived {
            // no need to handle messages in this test
        }

        // Wait for online status first
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_ONLINE, firstStatusReceivedOnline.await())

        chat.disconnectSubscriptions().await()
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_OFFLINE, statusReceivedOffline.await())

        chat.reconnectSubscriptions().await()
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_ONLINE, secondStatusReceivedOnline.await())

        statusListener.close()
        connect.close()

        assertEquals(2, onlineStatusCount)
    }

    @Test
    fun canHandleMultipleConnectionStatusListeners() = runTest {
        val statusReceivedOnline01 = CompletableDeferred<ConnectionStatusCategory>()
        val statusReceivedOnline02 = CompletableDeferred<ConnectionStatusCategory>()
        val statusReceivedOffline01 = CompletableDeferred<ConnectionStatusCategory>()
        val statusReceivedOffline02 = CompletableDeferred<ConnectionStatusCategory>()

        val connectionStatusListener01 = { status: ConnectionStatus ->
            when (status.category) {
                ConnectionStatusCategory.PN_CONNECTION_ONLINE -> {
                    statusReceivedOnline01.complete(status.category)
                }

                ConnectionStatusCategory.PN_CONNECTION_OFFLINE -> {
                    statusReceivedOffline01.complete(status.category)
                }

                else -> {
                    // do nothing for other statuses
                }
            }
            Unit
        }

        val connectionStatusListener02 = { status: ConnectionStatus ->
            when (status.category) {
                ConnectionStatusCategory.PN_CONNECTION_ONLINE -> {
                    statusReceivedOnline02.complete(status.category)
                }

                ConnectionStatusCategory.PN_CONNECTION_OFFLINE -> {
                    statusReceivedOffline02.complete(status.category)
                }

                else -> {
                    // do nothing for other statuses
                }
            }
            Unit
        }

        val statusListener01: AutoCloseable = chat.addConnectionStatusListener(connectionStatusListener01)
        val statusListener02: AutoCloseable = chat.addConnectionStatusListener(connectionStatusListener02)

        // we need to call connect to start EE that result triggering the online status
        val connect = channel01.onMessageReceived {
            // no need to handle messages in this test
        }
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_ONLINE, statusReceivedOnline01.await())
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_ONLINE, statusReceivedOnline02.await())

        chat.disconnectSubscriptions().await()
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_OFFLINE, statusReceivedOffline01.await())
        assertEquals(ConnectionStatusCategory.PN_CONNECTION_OFFLINE, statusReceivedOffline02.await())

        statusListener01.close()
        statusListener02.close()
        connect.close()
    }

    @Test
    fun inviteSameUserTwice_shouldHandleGracefully() = runTest {
        // given - a channel and a user to invite
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()
        val userToInvite = chat.createUser(UserImpl(chat, randomString(), name = "Test User")).await()

        // when - invite user the first time
        val firstInvite = testChannel.invite(userToInvite).await()
        assertNotNull(firstInvite, "First invite should succeed")
        assertEquals(userToInvite.id, firstInvite.user.id)
        assertEquals(testChannelId, firstInvite.channel.id)

        // when - invite the same user again
        val secondInvite = testChannel.invite(userToInvite).await()

        // then - should handle gracefully (idempotent operation)
        // The operation should either:
        // 1. Return the existing membership (preferred)
        // 2. Succeed without error
        assertNotNull(secondInvite, "Second invite should handle gracefully")
        assertEquals(userToInvite.id, secondInvite.user.id)
        assertEquals(testChannelId, secondInvite.channel.id)

        // verify - membership should exist and be consistent
        val members = testChannel.getMembers().await()
        val invitedUserMemberships = members.members.filter { it.user.id == userToInvite.id }
        assertEquals(1, invitedUserMemberships.size, "Should have exactly one membership for the user")

        // cleanup
        chat.deleteChannel(testChannelId).await()
        chat.deleteUser(userToInvite.id).await()
    }

    @Test
    fun sendText_afterChannelSoftDelete_shouldSucceed() = runTest {
        // given - create and delete a channel
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        // when - delete the channel (soft delete)
        testChannel.delete(soft = true).await()

        delayForHistory()

        // then - verify channel is soft-deleted
        val deletedChannel = chat.getChannel(testChannelId).await()
        assertNotNull(deletedChannel, "Channel should still exist after soft delete")
        assertEquals("deleted", deletedChannel.status, "Channel status should be 'deleted'")

        // when - try to send message to deleted channel
        // Note: PubNub allows publishing to any channel ID, even if channel metadata is deleted
        // This is because channels are implicit in PubNub - they exist when you publish to them
        val publishResult = deletedChannel.sendText("Message after delete").await()

        // then - publish should succeed (PubNub allows this)
        assertNotNull(publishResult, "Should be able to publish to deleted channel")
        assertTrue(publishResult.timetoken > 0, "Should get valid timetoken")

        // verify message was actually published
        delayForHistory()
        val message = deletedChannel.getMessage(publishResult.timetoken).await()
        assertNotNull(message, "Message should be retrievable even on deleted channel")
        assertEquals("Message after delete", message.text)

        // cleanup - hard delete
        chat.deleteChannel(testChannelId, soft = false).await()
    }

    @Test
    fun pinMessage_sameMessageTwice_shouldReplaceNotDuplicate() = runTest {
        // given - a test channel with two messages
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        val firstMessageText = "First message ${randomString()}"
        val secondMessageText = "Second message ${randomString()}"

        val firstPublishResult = testChannel.sendText(firstMessageText).await()
        val secondPublishResult = testChannel.sendText(secondMessageText).await()

        delayForHistory()

        val firstMessage = testChannel.getMessage(firstPublishResult.timetoken).await()!!
        val secondMessage = testChannel.getMessage(secondPublishResult.timetoken).await()!!

        // when - pin the first message
        val channelAfterFirstPin = testChannel.pinMessage(firstMessage).await()
        assertEquals(
            firstPublishResult.timetoken.toString(),
            channelAfterFirstPin.custom?.get(PINNED_MESSAGE_TIMETOKEN),
            "First message should be pinned"
        )

        delayForHistory()

        // when - pin the second message (should replace first)
        val channelAfterSecondPin = channelAfterFirstPin.pinMessage(secondMessage).await()
        assertEquals(
            secondPublishResult.timetoken.toString(),
            channelAfterSecondPin.custom?.get(PINNED_MESSAGE_TIMETOKEN),
            "Second message should replace first pinned message"
        )

        delayForHistory()

        // then - verify only second message is pinned
        val pinnedMessage = channelAfterSecondPin.getPinnedMessage().await()
        assertNotNull(pinnedMessage, "Should have a pinned message")
        assertEquals(
            secondMessage.timetoken,
            pinnedMessage.timetoken,
            "Pinned message should be the second message, not the first"
        )
        assertEquals(
            secondMessageText,
            pinnedMessage.text,
            "Pinned message text should match second message"
        )

        // cleanup
        chat.deleteChannel(testChannelId).await()
    }

    @Test
    fun threadMessage_pinToParentChannel_shouldBeRetrievableViaPinnedMessage() = runTest {
        // given - a parent channel with a message
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        val parentMessageText = "Parent message ${randomString()}"
        val parentPublishResult = testChannel.sendText(parentMessageText).await()
        delayForHistory()

        val parentMessage = testChannel.getMessage(parentPublishResult.timetoken).await()!!

        // when - create a thread and send a message
        val threadMessageText = "Thread message to pin ${randomString()}"
        val threadChannel = parentMessage.createThread(threadMessageText).await()
        delayForHistory()

        val threadHistory = threadChannel.getHistory().await()
        val threadMessage = threadHistory.messages.first()

        // then - pin the thread message to parent channel
        val updatedParentChannel = threadMessage.pinToParentChannel().await()

        // verify the pinned message metadata is set correctly
        assertEquals(
            threadMessage.timetoken.toString(),
            updatedParentChannel.custom?.get(PINNED_MESSAGE_TIMETOKEN),
            "Thread message timetoken should be pinned to parent channel"
        )

        delayForHistory()

        // verify the pinned message can be retrieved via getPinnedMessage
        val pinnedMessage = updatedParentChannel.getPinnedMessage().await()
        assertNotNull(pinnedMessage, "Should have a pinned message")
        assertEquals(
            threadMessage.timetoken,
            pinnedMessage.timetoken,
            "Retrieved pinned message timetoken should match"
        )
        assertEquals(
            threadMessageText,
            pinnedMessage.text,
            "Retrieved pinned message text should match thread message"
        )

        // verify unpinFromParentChannel works
        val channelAfterUnpin = threadMessage.unpinFromParentChannel().await()
        delayForHistory()

        val pinnedMessageAfterUnpin = channelAfterUnpin.getPinnedMessage().await()
        assertNull(pinnedMessageAfterUnpin, "Should have no pinned message after unpin")

        // cleanup
        parentMessage.removeThread().await()
        chat.deleteChannel(testChannelId).await()
    }

    @Test
    fun inviteMultiple_shouldInviteMultipleUsersSuccessfully() = runTest {
        // given - a channel and multiple users to invite
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        val user1Id = randomString()
        val user2Id = randomString()
        val user3Id = randomString()

        val user1 = chat.createUser(UserImpl(chat, user1Id, name = "Test User 1")).await()
        val user2 = chat.createUser(UserImpl(chat, user2Id, name = "Test User 2")).await()
        val user3 = chat.createUser(UserImpl(chat, user3Id, name = "Test User 3")).await()

        // when - invite multiple users
        val memberships = testChannel.inviteMultiple(listOf(user1, user2, user3)).await()

        // then - verify all memberships were created
        assertEquals(3, memberships.size, "Should have created 3 memberships")

        val memberUserIds = memberships.map { it.user.id }.toSet()
        assertTrue(memberUserIds.contains(user1Id), "User1 should be in memberships")
        assertTrue(memberUserIds.contains(user2Id), "User2 should be in memberships")
        assertTrue(memberUserIds.contains(user3Id), "User3 should be in memberships")

        // verify memberships have lastReadMessageTimetoken set
        memberships.forEach { membership ->
            assertNotNull(membership.lastReadMessageTimetoken, "lastReadMessageTimetoken should be set for ${membership.user.id}")
            assertTrue(membership.lastReadMessageTimetoken!! > 0, "lastReadMessageTimetoken should be positive")
        }

        // verify members can be retrieved
        val channelMembers = testChannel.getMembers().await()
        val channelMemberIds = channelMembers.members.map { it.user.id }.toSet()
        assertTrue(channelMemberIds.contains(user1Id), "User1 should be a channel member")
        assertTrue(channelMemberIds.contains(user2Id), "User2 should be a channel member")
        assertTrue(channelMemberIds.contains(user3Id), "User3 should be a channel member")

        // cleanup
        chat.deleteChannel(testChannelId).await()
        chat.deleteUser(user1Id).await()
        chat.deleteUser(user2Id).await()
        chat.deleteUser(user3Id).await()
    }

    @Test
    fun inviteMultiple_shouldHandleEmptyUsersList() = runTest {
        // given - a channel
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        // when - invite empty list of users
        val memberships = testChannel.inviteMultiple(emptyList()).await()

        // then - should return empty list without error
        assertTrue(memberships.isEmpty(), "Should return empty list for empty users input")

        // cleanup
        chat.deleteChannel(testChannelId).await()
    }
}

private fun Channel.asImpl(): ChannelImpl {
    return this as ChannelImpl
}
