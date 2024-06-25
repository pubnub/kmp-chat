package com.pubnub.integration

import com.pubnub.api.PubNubException
import com.pubnub.kmp.User
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_UPDATE_USER_METADATA
import com.pubnub.kmp.error.PubNubErrorMessage.USER_NOT_EXIST
import com.pubnub.kmp.utils.cyrb53a
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ChatIntegrationTest: BaseChatIntegrationTest() {

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

        val updatedUser = chat.updateUser(expectedUser.id, expectedUser.name, expectedUser.externalId, expectedUser.profileUrl, expectedUser.email, expectedUser.custom?.let { createCustomObject(it) }, expectedUser.status, expectedUser.type).await()

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
        val sortedUsers = listOf(chat.user.id, someUser.id).sorted()
        assertEquals("direct${cyrb53a("${sortedUsers[0]}&${sortedUsers[1]}")}", result.channel.id)

        assertEquals(chat.user, result.hostMembership.user.copy(updated = null, lastActiveTimestamp = null))
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
        assertEquals(chat.user, result.hostMembership.user.copy(updated = null, lastActiveTimestamp = null))
        assertEquals(otherUsers.size, result.inviteeMemberships.size)
        result.inviteeMemberships.forEach { inviteeMembership ->
            assertEquals(otherUsers.first { it.id == inviteeMembership.user.id }, inviteeMembership.user.copy(updated = null, lastActiveTimestamp = null))
            assertEquals(result.channel, inviteeMembership.channel)
        }

        assertEquals(result.channel, result.hostMembership.channel)
    }
}