package com.pubnub.chat.internal.metadata

import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEvent
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.User
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl

/**
 * Keeps the latest state of the users it was given and turns AppContext patches into full users.
 */
internal class UserEventTracker(private val chat: ChatInternal, users: Collection<User>) {
    private val trackedIds: Set<String> = users.map { it.id }.toSet()
    private var latestUsers: Map<String, User> = users.associateBy { it.id }

    fun updated(patch: PNUUIDMetadata): MetadataEvent.UserUpdated? {
        val userId = patch.id
        if (userId !in trackedIds) return null
        val newUser = latestUsers[userId]?.plus(patch) ?: UserImpl.fromDTO(chat, patch)
        latestUsers = latestUsers + (userId to newUser)
        return MetadataEvent.UserUpdated(newUser)
    }

    fun deleted(userId: String): MetadataEvent.UserDeleted? {
        if (userId !in trackedIds) return null
        latestUsers = latestUsers - userId
        return MetadataEvent.UserDeleted(userId)
    }
}

/**
 * Keeps the latest state of the channels it was given and turns AppContext patches into full channels.
 */
internal class ChannelEventTracker(private val chat: ChatInternal, channels: Collection<Channel>) {
    private val trackedIds: Set<String> = channels.map { it.id }.toSet()
    private var latestChannels: Map<String, Channel> = channels.associateBy { it.id }

    fun updated(patch: PNChannelMetadata): MetadataEvent.ChannelUpdated? {
        val channelId = patch.id
        if (channelId !in trackedIds) return null
        val newChannel = latestChannels[channelId]?.plus(patch) ?: ChannelImpl.fromDTO(chat, patch)
        latestChannels = latestChannels + (channelId to newChannel)
        return MetadataEvent.ChannelUpdated(newChannel)
    }

    fun deleted(channelId: String): MetadataEvent.ChannelDeleted? {
        if (channelId !in trackedIds) return null
        latestChannels = latestChannels - channelId
        return MetadataEvent.ChannelDeleted(channelId)
    }
}

/**
 * Keeps the latest state of the memberships it was given and turns AppContext patches into full memberships.
 */
internal class MembershipEventTracker(private val chat: ChatInternal, memberships: Collection<Membership>) {
    private val givenMemberships: List<Membership> = memberships.toList()
    private var latestMemberships: Map<Pair<String, String>, Membership> =
        givenMemberships.associateBy { it.channel.id to it.user.id }

    fun updated(channelId: String, patch: PNSetMembershipEvent): MetadataEvent.MembershipUpdated? {
        val key = channelId to patch.uuid
        val givenMembership = findGiven(key) ?: return null
        val newMembership = latestMemberships[key]?.plus(patch)
            ?: MembershipImpl(
                chat,
                channel = givenMembership.channel,
                user = givenMembership.user,
                custom = patch.custom?.value,
                updated = patch.updated,
                eTag = patch.eTag,
                status = patch.status?.value,
                type = patch.type?.value,
            )
        latestMemberships = latestMemberships + (key to newMembership)
        return MetadataEvent.MembershipUpdated(newMembership)
    }

    fun deleted(channelId: String, userId: String): MetadataEvent.MembershipDeleted? {
        val key = channelId to userId
        if (findGiven(key) == null) return null
        latestMemberships = latestMemberships - key
        return MetadataEvent.MembershipDeleted(channelId = channelId, userId = userId)
    }

    private fun findGiven(key: Pair<String, String>): Membership? =
        givenMemberships.find { it.channel.id == key.first && it.user.id == key.second }
}
