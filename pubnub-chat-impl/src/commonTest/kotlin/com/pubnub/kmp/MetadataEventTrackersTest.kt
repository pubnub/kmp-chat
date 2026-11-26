package com.pubnub.kmp

import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.utils.PatchValue
import com.pubnub.chat.Channel
import com.pubnub.chat.User
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.metadata.ChannelEventTracker
import com.pubnub.chat.internal.metadata.MembershipEventTracker
import com.pubnub.chat.internal.metadata.MetadataEvent
import com.pubnub.chat.internal.metadata.UserEventTracker
import com.pubnub.chat.types.ChannelType
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetadataEventTrackersTest {
    private val chat: ChatInternal = mock(MockMode.strict)

    private fun user(id: String = "user1", name: String? = "Old name", email: String? = "old@mail") =
        UserImpl(chat, id = id, name = name, email = email, custom = mapOf("a" to 1))

    private fun channel(id: String = "channel1", name: String? = "Old name", description: String? = "Old description") =
        ChannelImpl(chat, id = id, name = name, description = description, type = ChannelType.GROUP)

    @Test
    fun userUpdate_isMergedIntoGivenUser() {
        val tracker = UserEventTracker(chat, listOf(user()))

        val event = tracker.updated(PNUUIDMetadata(id = "user1", name = PatchValue.of("New name")))

        val updated = (event as MetadataEvent.UserUpdated).user
        assertEquals("New name", updated.name)
        assertEquals("old@mail", updated.email)
        assertEquals(mapOf("a" to 1), updated.custom)
    }

    @Test
    fun userUpdates_buildOnTheLatestState() {
        val tracker = UserEventTracker(chat, listOf(user()))

        tracker.updated(PNUUIDMetadata(id = "user1", name = PatchValue.of("Second name")))
        val event = tracker.updated(PNUUIDMetadata(id = "user1", email = PatchValue.of("new@mail")))

        val updated = (event as MetadataEvent.UserUpdated).user
        assertEquals("Second name", updated.name)
        assertEquals("new@mail", updated.email)
    }

    @Test
    fun userUpdate_ofUserThatWasNotGiven_isIgnored() {
        val tracker = UserEventTracker(chat, listOf(user()))

        assertNull(tracker.updated(PNUUIDMetadata(id = "other", name = PatchValue.of("x"))))
        assertNull(tracker.deleted("other"))
    }

    @Test
    fun userDelete_isTranslatedToUserDeleted() {
        val tracker = UserEventTracker(chat, listOf(user()))

        assertEquals(MetadataEvent.UserDeleted("user1"), tracker.deleted("user1"))
    }

    @Test
    fun userUpdateAfterDelete_isBuiltFromThePatchOnly() {
        val tracker = UserEventTracker(chat, listOf(user()))
        tracker.deleted("user1")

        val event = tracker.updated(PNUUIDMetadata(id = "user1", name = PatchValue.of("Recreated")))

        val recreated: User = (event as MetadataEvent.UserUpdated).user
        assertEquals("Recreated", recreated.name)
        assertNull(recreated.email)
    }

    @Test
    fun channelUpdate_isMergedIntoGivenChannel() {
        val tracker = ChannelEventTracker(chat, listOf(channel()))

        val event = tracker.updated(PNChannelMetadata(id = "channel1", name = PatchValue.of("New name")))

        val updated: Channel = (event as MetadataEvent.ChannelUpdated).channel
        assertEquals("New name", updated.name)
        assertEquals("Old description", updated.description)
        assertEquals(ChannelType.GROUP, updated.type)
    }

    @Test
    fun channelUpdates_buildOnTheLatestState() {
        val tracker = ChannelEventTracker(chat, listOf(channel()))

        tracker.updated(PNChannelMetadata(id = "channel1", name = PatchValue.of("Second name")))
        val event = tracker.updated(PNChannelMetadata(id = "channel1", description = PatchValue.of("New description")))

        val updated = (event as MetadataEvent.ChannelUpdated).channel
        assertEquals("Second name", updated.name)
        assertEquals("New description", updated.description)
    }

    @Test
    fun channelUpdate_ofChannelThatWasNotGiven_isIgnored() {
        val tracker = ChannelEventTracker(chat, listOf(channel()))

        assertNull(tracker.updated(PNChannelMetadata(id = "other", name = PatchValue.of("x"))))
        assertNull(tracker.deleted("other"))
    }

    @Test
    fun channelDelete_isTranslatedToChannelDeleted() {
        val tracker = ChannelEventTracker(chat, listOf(channel()))

        assertEquals(MetadataEvent.ChannelDeleted("channel1"), tracker.deleted("channel1"))
    }

    @Test
    fun membershipDelete_isTranslatedToMembershipDeleted_onlyForGivenMemberships() {
        val membership = MembershipImpl(chat, channel(), user(), null, null, null, null, null)
        val tracker = MembershipEventTracker(chat, listOf(membership))

        assertEquals(
            MetadataEvent.MembershipDeleted(channelId = "channel1", userId = "user1"),
            tracker.deleted("channel1", "user1")
        )
        assertNull(tracker.deleted("channel1", "other-user"))
        assertNull(tracker.deleted("other-channel", "user1"))
    }
}
