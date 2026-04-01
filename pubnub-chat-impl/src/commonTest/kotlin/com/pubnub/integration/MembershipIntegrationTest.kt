package com.pubnub.integration

import com.pubnub.chat.Membership
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MembershipIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun streamUpdatesOn() = runTest {
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
        val membership1 = channel01.join().await()
        val membership2 = channel02.join().await()
        delayInMillis(1000)
        val updatedStatus = "updatedStatus"
        val updatedType = "updatedType"
        val updatedCustom = mapOf("a" to "b")

        val expectedUpdates = listOf<List<Membership>>(
            listOf(
                membership1.asImpl().copy(custom = updatedCustom, status = updatedStatus, type = updatedType),
                membership2.asImpl().copy()
            ),
            listOf(membership1.asImpl().copy(custom = updatedCustom, status = updatedStatus, type = updatedType)),
            emptyList()
        )
        val actualUpdates = mutableListOf<List<Membership>>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id, channel02.id)) {
                dispose = MembershipImpl.streamUpdatesOn(listOf(membership1, membership2)) { memberships ->
                    actualUpdates.add(memberships.map { it.asImpl().copy(updated = null, eTag = null) }.sortedBy { it.channel.id })
                }
            }

            membership1.update(
                status = updatedStatus,
                type = updatedType,
                custom = createCustomObject(updatedCustom)
            ).await()
            channel02.leave().await()
            channel01.leave().await()
            delayInMillis(1000)
            dispose?.close()
        }
        assertEquals(
            expectedUpdates.map { membershipList ->
                membershipList.map { membership ->
                    membership.asImpl().copy(updated = null, eTag = null) as Membership
                }.sortedBy { it.channel.id }
            },
            actualUpdates
        )
    }

    @Test
    fun setLastReadMessageTimetoken() = runTest {
        val timetoken = 1000L
        val channel = chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let {
                createCustomObject(it)
            },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership1 = channel.join().await()

        val membershipUpdated = membership1.setLastReadMessageTimetoken(timetoken).await()

        assertEquals(timetoken, membershipUpdated.lastReadMessageTimetoken)
    }

    @Test
    fun setLastReadMessage() = runTest {
        val timetoken = 1000L
        val channel = chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let {
                createCustomObject(it)
            },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership1 = channel.join().await()

        val membershipUpdated = membership1.setLastReadMessage(
            MessageImpl(
                chat,
                timetoken,
                EventContent.TextMessageContent("abc"),
                channelId = channel.id,
                userId = someUser.id
            )
        ).await()

        assertEquals(timetoken, membershipUpdated.lastReadMessageTimetoken)
    }

    @Test
    fun update() = runTest {
        val expectedStatus = "updatedStatus"
        val expectedType = "updatedType"
        val expectedCustom = mapOf("role" to "moderator")
        val channel = chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let {
                createCustomObject(it)
            },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership = channel.join().await()

        val updatedMembership = membership.update(
            status = expectedStatus,
            type = expectedType,
            custom = createCustomObject(expectedCustom)
        ).await()

        assertEquals(expectedStatus, updatedMembership.status)
        assertEquals(expectedType, updatedMembership.type)
        assertEquals(expectedCustom, updatedMembership.custom)
    }

    @Test
    fun updatePartially() = runTest {
        val initialStatus = "memberStatus"
        val initialType = "memberType"
        val initialCustom = mapOf("role" to "member")
        val channel = chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let {
                createCustomObject(it)
            },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership = channel.join(
            status = initialStatus,
            type = initialType,
            custom = createCustomObject(initialCustom)
        ).await()

        val updatedMembership = membership.update(status = "moderatorStatus").await()

        assertEquals("moderatorStatus", updatedMembership.status)
        assertEquals(initialType, updatedMembership.type)
        assertEquals(initialCustom, updatedMembership.custom)
    }

    @Test
    fun updatePartially_preserves_unchanged_fields_when_only_type_is_updated() = runTest {
        val initialStatus = "memberStatus"
        val initialType = "memberType"
        val initialCustom = mapOf("role" to "member")
        val updatedType = "moderatorType"
        val channel = chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let {
                createCustomObject(it)
            },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership = channel.join(
            status = initialStatus,
            type = initialType,
            custom = createCustomObject(initialCustom)
        ).await()

        val updatedMembership = membership.update(type = updatedType).await()

        assertEquals(updatedType, updatedMembership.type)
        assertEquals(initialStatus, updatedMembership.status)
        assertEquals(initialCustom, updatedMembership.custom)
    }

    @Test
    fun onUpdated() = runTest {
        chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership = channel01.join().await()
        delayInMillis(1000)
        val expectedStatus = "updatedStatus"
        val expectedType = "updatedType"
        val expectedCustom = mapOf("a" to "b")

        val completableMembership = CompletableDeferred<Membership>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                dispose = membership.onUpdated { updatedMembership ->
                    completableMembership.complete(updatedMembership)
                }
            }
            membership.update(
                status = expectedStatus,
                type = expectedType,
                custom = createCustomObject(expectedCustom)
            ).await()
            val updatedMembership = completableMembership.await()
            assertEquals(expectedCustom, updatedMembership.custom)
            assertEquals(expectedStatus, updatedMembership.status)
            assertEquals(expectedType, updatedMembership.type)

            dispose?.close()
        }
    }

    @Test
    fun update_with_custom_preserves_lastReadMessageTimetoken() = runTest {
        val timetoken = 1000L
        val channel = chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership = channel.join().await()
        val membershipWithTimetoken = membership.setLastReadMessageTimetoken(timetoken).await()
        assertEquals(timetoken, membershipWithTimetoken.lastReadMessageTimetoken)

        val updatedMembership = membershipWithTimetoken.update(
            custom = createCustomObject(mapOf("role" to "moderator"))
        ).await()

        assertEquals(timetoken, updatedMembership.lastReadMessageTimetoken)
        assertEquals("moderator", updatedMembership.custom?.get("role"))
    }

    @Test
    fun update_with_custom_containing_explicit_lastReadMessageTimetoken_uses_provided_value() = runTest {
        val initialTimetoken = 1000L
        val newTimetoken = 2000L
        val channel = chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership = channel.join().await()
        val membershipWithTimetoken = membership.setLastReadMessageTimetoken(initialTimetoken).await()
        assertEquals(initialTimetoken, membershipWithTimetoken.lastReadMessageTimetoken)

        val updatedMembership = membershipWithTimetoken.update(
            custom = createCustomObject(
                mapOf(
                    "role" to "moderator",
                    "lastReadMessageTimetoken" to newTimetoken.toString()
                )
            )
        ).await()

        assertEquals(newTimetoken, updatedMembership.lastReadMessageTimetoken)
        assertEquals("moderator", updatedMembership.custom?.get("role"))
    }

    @Test
    fun onDeleted() = runTest {
        chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()
        delayInMillis(1000)
        val membership = channel01.join().await()
        delayInMillis(1000)

        val completableDeleted = CompletableDeferred<Unit>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                dispose = membership.onDeleted {
                    completableDeleted.complete(Unit)
                }
            }
            channel01.leave().await()
            completableDeleted.await()

            dispose?.close()
        }
    }
}

private fun Membership.asImpl(): MembershipImpl {
    return this as MembershipImpl
}
