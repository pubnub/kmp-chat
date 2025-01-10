package com.pubnub.integration

import com.pubnub.api.models.consumer.pubsub.PNEvent
import com.pubnub.chat.internal.PREFIX_PUBNUB_PRIVATE
import com.pubnub.chat.internal.mutelist.MutedUsersImpl
import com.pubnub.chat.mutelist.MutedUsers
import com.pubnub.test.await
import com.pubnub.test.test
import delayForHistory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

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
    fun sync_updates_between_clients() = runTest(timeout = 10.seconds) {
        val mutedUsers1 = getMutedUsers(true)
        val mutedUsers2 = MutedUsersImpl(pubnub02, pubnub.configuration.userId.value, true)
        pubnub.test(backgroundScope) {
            pubnub.awaitSubscribe(listOf("aaa")) // just to kick off connection
            pubnub.awaitSubscribe(listOf("${PREFIX_PUBNUB_PRIVATE}${pubnub.configuration.userId.value}.mute1")) {
                // custom subscription block empty, let mutedUsers subscribe for us
            }
            delayForHistory()
            mutedUsers2.muteUser(someUser02.id).await()
            nextEvent<PNEvent>()
            assertContains(mutedUsers1.muteSet, someUser02.id)
        }
    }
}
