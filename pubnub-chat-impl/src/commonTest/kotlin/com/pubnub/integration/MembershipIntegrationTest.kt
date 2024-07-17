package com.pubnub.integration

import com.pubnub.chat.Membership
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MembershipIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun streamUpdatesOn() = runTest(timeout = defaultTimeout) {
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
        val membership1 = channel01.join { }.await().also { it.disconnect.close() }.membership
        val membership2 = channel02.join { }.await().also { it.disconnect.close() }.membership
        delayInMillis(1000)

        val expectedUpdates = listOf<List<Membership>>(
            listOf(
                membership1.asImpl().copy(custom = mapOf("a" to "b"), updated = null, eTag = null),
                membership2.asImpl().copy(updated = null, eTag = null)
            ).sortedBy {
                it.channel.id
            },
            listOf(membership1.asImpl().copy(custom = mapOf("a" to "b"), updated = null, eTag = null)).sortedBy { it.channel.id },
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

            membership1.update(createCustomObject(mapOf("a" to "b"))).await()
            channel02.leave().await()
            channel01.leave().await()
            delayInMillis(1000)
            dispose?.close()
        }
        assertEquals(expectedUpdates, actualUpdates)
    }
}

private fun Membership.asImpl(): MembershipImpl {
    return this as MembershipImpl
}
