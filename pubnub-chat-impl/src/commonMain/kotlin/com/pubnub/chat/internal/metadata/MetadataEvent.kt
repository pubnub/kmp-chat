package com.pubnub.chat.internal.metadata

import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.User

/**
 * Storage-independent metadata event. Updated events carry the full entity, already merged with
 * the latest known state, so the receiver never has to deal with patches.
 */
internal sealed interface MetadataEvent {
    data class UserUpdated(val user: User) : MetadataEvent

    data class UserDeleted(val userId: String) : MetadataEvent

    data class ChannelUpdated(val channel: Channel) : MetadataEvent

    data class ChannelDeleted(val channelId: String) : MetadataEvent

    data class MembershipUpdated(val membership: Membership) : MetadataEvent

    data class MembershipDeleted(val channelId: String, val userId: String) : MetadataEvent
}
