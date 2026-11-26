package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.chat.internal.mutelist.MutedUsersManagerImpl
import com.pubnub.kmp.utils.FakeMetadataRepository
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutedUsersManagerImplTest {
    private val pubNub: PubNub = mock(MockMode.strict)
    private val repository = FakeMetadataRepository()
    private val userId = "me"
    private val storageId = "PN_PRV.me.mute1"

    private fun createManager(syncEnabled: Boolean): MutedUsersManagerImpl {
        if (syncEnabled) {
            every { pubNub.addListener(any()) } returns Unit
        }
        return MutedUsersManagerImpl(pubNub, userId, syncEnabled, repository)
    }

    @Test
    fun loadMutedUsers_readsTheMuteListFromTheRepository() = runTest {
        repository.storedMutedUsers[storageId] = setOf("a", "b")
        val manager = createManager(syncEnabled = true)

        manager.loadMutedUsers().await()

        assertEquals(listOf(storageId), repository.getMutedUsersCalls)
        assertEquals(setOf("a", "b"), manager.mutedUsers)
    }

    @Test
    fun loadMutedUsers_whenNothingIsStored_resultsInEmptyMuteList() = runTest {
        val manager = createManager(syncEnabled = true)

        manager.loadMutedUsers().await()

        assertTrue(manager.mutedUsers.isEmpty())
    }

    @Test
    fun muteUser_savesTheWholeMuteListInTheRepository() = runTest {
        val manager = createManager(syncEnabled = true)

        manager.muteUser("a").await()
        manager.muteUser("b").await()

        assertEquals(setOf("a", "b"), manager.mutedUsers)
        assertEquals(
            listOf(
                Triple(storageId, userId, setOf("a")),
                Triple(storageId, userId, setOf("a", "b")),
            ),
            repository.setMutedUsersCalls
        )
    }

    @Test
    fun unmuteUser_savesTheMuteListWithoutTheUser() = runTest {
        repository.storedMutedUsers[storageId] = setOf("a", "b")
        val manager = createManager(syncEnabled = true)
        manager.loadMutedUsers().await()

        manager.unmuteUser("a").await()

        assertEquals(setOf("b"), manager.mutedUsers)
        assertEquals(setOf("b"), repository.storedMutedUsers[storageId])
    }

    @Test
    fun muteListChangedElsewhere_isObservedThroughTheRepository() {
        val manager = createManager(syncEnabled = true)

        repository.emitMutedUsers(storageId, setOf("x", "y"))
        assertEquals(setOf("x", "y"), manager.mutedUsers)

        repository.emitMutedUsers(storageId, emptySet())
        assertTrue(manager.mutedUsers.isEmpty())
    }

    @Test
    fun whenSyncIsDisabled_repositoryIsNotUsed() = runTest {
        val manager = createManager(syncEnabled = false)

        manager.loadMutedUsers().await()
        manager.muteUser("a").await()

        assertEquals(setOf("a"), manager.mutedUsers)
        assertTrue(repository.getMutedUsersCalls.isEmpty())
        assertTrue(repository.setMutedUsersCalls.isEmpty())
        assertEquals(0, repository.activeMutedUsersStreams)
    }
}
