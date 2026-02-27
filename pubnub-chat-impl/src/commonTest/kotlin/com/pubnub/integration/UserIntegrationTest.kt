package com.pubnub.integration

import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.chat.Chat
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.user.Invite
import com.pubnub.chat.user.Mention
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class UserIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun getChannelRestrictions() = runTest {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }
        val channelId = "channelId01"
        val channel = ChannelImpl(chat = chatPamServer, id = channelId)
        val ban = true
        val mute = true
        val reason = "rude"

        userPamServer.setRestrictions(channel = channel, ban = ban, mute = mute, reason = reason).await()

        val restriction: Restriction = userPamServer.getChannelRestrictions(channel).await()
        assertEquals(userPamServer.id, restriction.userId)
        assertEquals(channelId, restriction.channelId)
        assertEquals(ban, restriction.ban)
        assertEquals(mute, restriction.mute)
        assertEquals(reason, restriction.reason)
    }

    @Test
    fun getChannelsRestrictions_sortAsc() = runTest {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }
        val channelId01 = "channelId01"
        val channelId02 = "channelId02"
        val ban = true
        val mute = true
        val reason = "rude"
        val limit = 2
        val page = null
        val sort: Collection<PNSortKey<PNMembershipKey>> = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))

        userPamServer.setRestrictions(
            channel = ChannelImpl(chat = chatPamServer, id = channelId01),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()
        userPamServer.setRestrictions(
            channel = ChannelImpl(chat = chatPamServer, id = channelId02),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()

        val getRestrictionsResponse = userPamServer.getChannelsRestrictions(limit = limit, page = page, sort = sort).await()

        assertEquals(limit, getRestrictionsResponse.total)
        assertEquals(200, getRestrictionsResponse.status)
        val firstRestriction = getRestrictionsResponse.restrictions.first()
        assertEquals(channelId01, firstRestriction.channelId)
        assertEquals(ban, firstRestriction.ban)
        assertEquals(mute, firstRestriction.mute)
        assertEquals(reason, firstRestriction.reason)
        val secondRestriction = getRestrictionsResponse.restrictions.elementAt(1)
        assertEquals(channelId02, secondRestriction.channelId)
        assertEquals(ban, secondRestriction.ban)
        assertEquals(mute, secondRestriction.mute)
        assertEquals(reason, secondRestriction.reason)
    }

    @Test
    fun getChannelsRestrictions_sortDsc() = runTest {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }
        val channelId01 = "channelId01"
        val channelId02 = "channelId02"
        val ban = true
        val mute = true
        val reason = "rude"
        val limit = 2
        val page = null
        val sort: Collection<PNSortKey<PNMembershipKey>> = listOf(PNSortKey.PNDesc(PNMembershipKey.CHANNEL_ID))

        userPamServer.setRestrictions(
            channel = ChannelImpl(chat = chatPamServer, id = channelId01),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()
        userPamServer.setRestrictions(
            channel = ChannelImpl(chat = chatPamServer, id = channelId02),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()

        val getRestrictionsResponse: GetRestrictionsResponse =
            userPamServer.getChannelsRestrictions(limit = limit, page = page, sort = sort).await()

        val firstRestriction = getRestrictionsResponse.restrictions.first()
        assertEquals(channelId02, firstRestriction.channelId)
        val secondRestriction = getRestrictionsResponse.restrictions.elementAt(1)
        assertEquals(channelId01, secondRestriction.channelId)
    }

    @Test
    fun updateUser() = runTest {
        val user = chat.createUser(someUser).await()
        val expectedUser = user.asImpl().copy(
            name = randomString(),
            externalId = randomString(),
            profileUrl = randomString(),
            email = randomString(),
            custom = mapOf(
                randomString() to randomString()
            ),
            type = randomString(),
            updated = null,
            eTag = null
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

        assertEquals(expectedUser, updatedUser.asImpl().copy(updated = null, lastActiveTimestamp = null, eTag = null))
        assertNotNull(updatedUser.updated)
    }

    @Test
    fun updateUser_withEtag() = runTest {
        val user = chat.createUser(
            someUser.id,
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            createCustomObject(mapOf("def" to randomString())),
            randomString(),
            randomString()
        ).await()

        val updatedUser = user.update { previousUser ->
            name = previousUser.name + "1"
            externalId = previousUser.externalId + "1"
            profileUrl = previousUser.profileUrl + "1"
            email = previousUser.email + "1"
            custom = createCustomObject(previousUser.custom!! + ("abc" to 1))
            type = previousUser.type + "1"
            status = previousUser.status + "1"
        }.await()

        assertEquals(user.name + "1", updatedUser.name)
        assertEquals(user.externalId + "1", updatedUser.externalId)
        assertEquals(user.profileUrl + "1", updatedUser.profileUrl)
        assertEquals(user.email + "1", updatedUser.email)
        assertEquals(user.custom!!["def"], updatedUser.custom!!["def"])
        assertEquals(1, (updatedUser.custom!!["abc"] as Number).toInt())
        assertEquals(user.type + "1", updatedUser.type)
        assertEquals(user.status + "1", updatedUser.status)
        assertNotEquals(user.updated, updatedUser.updated)
        assertNotEquals(user.eTag, updatedUser.eTag)
    }

    @Test
    fun updateUser_withEtag_withNullValue() = runTest {
        val user = chat.createUser(
            someUser.id,
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            createCustomObject(mapOf("def" to randomString())),
            randomString(),
            randomString()
        ).await()

        val updatedUser = user.update { previousUser ->
            name = previousUser.name + "1"
            externalId = null
            profileUrl = previousUser.profileUrl + "1"
            email = previousUser.email + "1"
            custom = createCustomObject(previousUser.custom!! + ("abc" to 1))
            type = previousUser.type + "1"
            status = previousUser.status + "1"
        }.await()

        assertEquals(user.name + "1", updatedUser.name)
        assertEquals(user.externalId, updatedUser.externalId)
        assertEquals(user.profileUrl + "1", updatedUser.profileUrl)
        assertEquals(user.email + "1", updatedUser.email)
        assertEquals(user.custom!!["def"], updatedUser.custom!!["def"])
        assertEquals(1, (updatedUser.custom!!["abc"] as Number).toInt())
        assertEquals(user.type + "1", updatedUser.type)
        assertEquals(user.status + "1", updatedUser.status)
        assertNotEquals(user.updated, updatedUser.updated)
        assertNotEquals(user.eTag, updatedUser.eTag)
    }

    @Test
    fun streamUpdatesOn() = runTest {
        val newName = "newName"
        val expectedUpdates = listOf(
            listOf(someUser),
            listOf(someUser.asImpl().copy(name = newName)),
            listOf(someUser.asImpl().copy(name = newName, externalId = newName)),
            emptyList()
        )
        val actualUpdates = mutableListOf<List<User>>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(someUser.id)) {
                dispose = UserImpl.streamUpdatesOn(listOf(someUser)) {
                    actualUpdates.add(it.map { it.asImpl().copy(updated = null, eTag = null) })
                }
            }

            chat.createUser(someUser).await()

            someUser.update(name = newName).await()

            someUser.update(externalId = newName).await()

            someUser.delete().await()

            delayInMillis(2000)
            dispose?.close()
        }

        assertEquals(expectedUpdates, actualUpdates)
    }

    @Test
    fun whenUserDoesNotExist_init_should_create_it_with_lastActiveTimestamp() = runTest {
        // set up storeUserActivityTimestamps
        val chatConfig = ChatConfiguration(
            storeUserActivityTimestamps = true
        )
        val chatNew: Chat = createChat { ChatImpl(chatConfig, pubnub) }.initialize().await()
        val someUser = chatNew.currentUser

        // when
        val isUserActive = someUser.active

        // then
        assertTrue(isUserActive)
    }

    @Test
    fun whenUserExists_init_should_update_lastActiveTimestamp() = runTest {
        // set up storeUserActivityTimestamps
        val chatConfig = ChatConfiguration(
            storeUserActivityTimestamps = true
        )

        val chatNew: Chat = createChat { ChatImpl(chatConfig, pubnub) }.initialize().await()
        delayInMillis(2000)
        // call init second time to simulate user existence
        val chatNew2: Chat = ChatImpl(chatConfig, pubnub).initialize().await()
        val someUser = chatNew2.currentUser

        // when
        val isUserActive = someUser.active

        // then
        assertTrue(isUserActive)
    }

    @Test
    fun whenUserHasRestriction_GetMembershipShouldNotReturnedInternalModerationChannel() = runTest {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }
        val mute = true
        val ban = true
        val reason = "rude"
        userPamServer.setRestrictions(channel = channelPam, mute = true, ban = true, reason = reason).await()
        val restrictions = userPamServer.getChannelRestrictions(channel = channelPam).await()
        assertEquals(mute, restrictions.mute)
        assertEquals(ban, restrictions.ban)
        assertEquals(reason, restrictions.reason)

        val membershipsResponse: MembershipsResponse = userPamServer.getMemberships().await()
        val internalModerationChannelCount = membershipsResponse.memberships.filter { membership ->
            membership.channel.id.contains(INTERNAL_MODERATION_PREFIX)
        }.size
        assertEquals(0, internalModerationChannelCount)
    }

    @Test
    fun onUpdated() = runTest {
        val newName = "newName_${randomString()}"
        val result = CompletableDeferred<User>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(someUser.id)) {
                dispose = someUser.onUpdated { user ->
                    result.complete(user)
                }
            }
            someUser.update(name = newName).await()
            val updated = result.await()
            assertEquals(newName, updated.name)
            assertEquals(someUser.id, updated.id)
            dispose?.close()
        }
    }

    @Test
    fun onDeleted() = runTest {
        val deleted = CompletableDeferred<Unit>()
        val user = chat.createUser(randomString()).await()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(user.id)) {
                dispose = user.onDeleted {
                    deleted.complete(Unit)
                }
            }
            user.delete().await()
            deleted.await()
            dispose?.close()
        }
    }

    @Test
    fun onMentioned() = runTest(timeout = 10.seconds) {
        val result = CompletableDeferred<Mention>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(someUser.id)) {
                dispose = someUser.onMentioned { mention ->
                    result.complete(mention)
                }
            }
            channel01.sendText(
                text = "Hello @user",
                usersToMention = listOf(someUser.id)
            ).await()

            val mention = result.await()
            assertEquals(channel01.id, mention.channelId)
            dispose?.close()
        }
    }

    @Test
    fun onInvited() = runTest(timeout = 10.seconds) {
        val result = CompletableDeferred<Invite>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(someUser.id)) {
                dispose = someUser.onInvited { invite ->
                    result.complete(invite)
                }
            }

            channel01.invite(someUser).await()
            val invite = result.await()
            assertEquals(channel01.id, invite.channelId)
            dispose?.close()
        }
    }

    @Test
    fun onRestrictionChanged() = runTest(timeout = 10.seconds) {
        if (isIos()) {
            println("Skipping test on iOS")
            return@runTest
        }

        val result = CompletableDeferred<Restriction>()
        val channel = ChannelImpl(chat = chatPamServer, id = "channelId_${randomString()}")

        pubnubPamServer.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnubPamServer.awaitSubscribe(listOf("PUBNUB_INTERNAL_MODERATION.${userPamServer.id}")) {
                dispose = userPamServer.onRestrictionChanged { restriction ->
                    result.complete(restriction)
                }
            }

            userPamServer.setRestrictions(
                channel = channel,
                ban = true,
                mute = false,
                reason = "rude"
            ).await()

            val restriction = result.await()
            assertEquals(userPamServer.id, restriction.userId)
            assertEquals(channel.id, restriction.channelId)
            assertTrue(restriction.ban)
            assertEquals("rude", restriction.reason)
            dispose?.close()
        }
    }
}
