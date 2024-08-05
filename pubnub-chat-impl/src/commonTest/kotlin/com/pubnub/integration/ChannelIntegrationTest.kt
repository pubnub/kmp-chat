package com.pubnub.integration

import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.User
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ChannelIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun join() = runTest {
        val channelId = randomString()
        val channel = chat.createChannel(channelId).await()

        val result = channel.join {}.await()

        assertEquals(config.userId.value, result.membership.user.id)
        assertEquals(channel.id, result.membership.channel.id)

        chat.deleteChannel(channelId).await()
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

        channelPam.setRestrictions(user = UserImpl(chat = chatPam, id = userId01), ban = ban, mute = mute, reason = reason)
            .await()
        channelPam.setRestrictions(user = UserImpl(chat = chatPam, id = userId02), ban = ban, mute = mute, reason = reason)
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

    // todo fix
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

        // todo there are big problems with update handling in PN SDK that prevent this for working in a sane way
        // e.g. getting an update to one property (e.g. description) will return an object with all other properties set to null
        val expectedUpdates = listOf<List<Channel>>(
            listOf(
                channel01.asImpl().copy(
                    name = newName,
                    custom = null,
                    description = null,
                    type = ChannelType.UNKNOWN,
                    status = null,
                    updated = null
                ),
                channel02.asImpl().copy(updated = null)
            ).sortedBy {
                it.id
            },
            listOf(
                channel01.asImpl().copy(
                    name = newName,
                    custom = null,
                    description = null,
                    type = ChannelType.UNKNOWN,
                    status = null,
                    updated = null
                ),
                channel02.asImpl().copy(
                    custom = null,
                    name = null,
                    description = newName,
                    type = ChannelType.UNKNOWN,
                    status = null,
                    updated = null
                )
            ).sortedBy {
                it.id
            },
            listOf(
                channel02.asImpl().copy(
                    custom = null,
                    name = null,
                    description = newName,
                    type = ChannelType.UNKNOWN,
                    status = null,
                    updated = null
                )
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

        assertEquals(expectedUpdates, actualUpdates)
    }

    @Test
    fun getMessage() = runTest(timeout = 10.seconds) {
        val messageText = "some text"
        val channelId = randomString()
        val channel = chat.createChannel(channelId).await()
        val tt = channel.sendText(messageText, ttl = 60).await().timetoken

        delayInMillis(150)

        val message = channel.getMessage(tt).await()
        assertEquals(messageText, message?.text)
        assertEquals(tt, message?.timetoken)

        chat.deleteChannel(channelId).await()
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
}

private fun Channel.asImpl(): ChannelImpl {
    return this as ChannelImpl
}
