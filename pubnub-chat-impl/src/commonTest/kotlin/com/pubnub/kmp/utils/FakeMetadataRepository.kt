package com.pubnub.kmp.utils

import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.metadata.MemberInput
import com.pubnub.chat.internal.metadata.MembershipInput
import com.pubnub.chat.internal.metadata.MetadataEvent
import com.pubnub.chat.internal.metadata.MetadataRepository
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture

/**
 * In-memory [MetadataRepository] for tests. Only the mute list is implemented, because that is the only part
 * used without a Chat instance. Every other operation fails the test that unexpectedly uses it.
 */
internal class FakeMetadataRepository : MetadataRepository {
    /** Mute lists by storage id. */
    val storedMutedUsers: MutableMap<String, Set<String>> = mutableMapOf()

    val getMutedUsersCalls: MutableList<String> = mutableListOf()
    val setMutedUsersCalls: MutableList<Triple<String, String, Set<String>>> = mutableListOf()

    private val mutedUsersCallbacks: MutableList<Pair<String, (Set<String>) -> Unit>> = mutableListOf()

    /** Number of mute list streams that were started and not closed yet. */
    val activeMutedUsersStreams: Int get() = mutedUsersCallbacks.size

    /** Simulates a change of the mute list made elsewhere (for example by another device). */
    fun emitMutedUsers(storageId: String, mutedUsers: Set<String>) {
        mutedUsersCallbacks.filter { it.first == storageId }.forEach { it.second(mutedUsers) }
    }

    override fun getMutedUsers(mutedUsersStorageId: String): PNFuture<Set<String>> {
        getMutedUsersCalls += mutedUsersStorageId
        return (storedMutedUsers[mutedUsersStorageId] ?: emptySet()).asFuture()
    }

    override fun setMutedUsers(
        mutedUsersStorageId: String,
        ownerUserId: String,
        mutedUsers: Set<String>,
    ): PNFuture<Unit> {
        setMutedUsersCalls += Triple(mutedUsersStorageId, ownerUserId, mutedUsers)
        storedMutedUsers[mutedUsersStorageId] = mutedUsers
        return Unit.asFuture()
    }

    override fun streamMutedUsers(mutedUsersStorageId: String, callback: (Set<String>) -> Unit): AutoCloseable {
        val entry = mutedUsersStorageId to callback
        mutedUsersCallbacks += entry
        return AutoCloseable { mutedUsersCallbacks.remove(entry) }
    }

    private fun notUsed(): Nothing = error("Not expected to be used in this test")

    override fun getUser(chat: ChatInternal, userId: String): PNFuture<User?> = notUsed()

    override fun getUsers(
        chat: ChatInternal,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetUsersResponse> = notUsed()

    override fun setUser(
        chat: ChatInternal,
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        type: String?,
        status: String?,
    ): PNFuture<User> = notUsed()

    override fun updateUser(chat: ChatInternal, user: User, values: User.UpdatableValues): PNFuture<User> = notUsed()

    override fun removeUser(userId: String): PNFuture<Unit> = notUsed()

    override fun getChannel(chat: ChatInternal, channelId: String): PNFuture<Channel?> = notUsed()

    override fun getChannels(
        chat: ChatInternal,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetChannelsResponse> = notUsed()

    override fun setChannel(
        chat: ChatInternal,
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?,
    ): PNFuture<Channel> = notUsed()

    override fun setChannelCustom(chat: ChatInternal, channelId: String, custom: CustomObject): PNFuture<Channel> =
        notUsed()

    override fun setThreadChannelDescription(threadChannelId: String, description: String?): PNFuture<Unit> =
        notUsed()

    override fun getThreadChannel(
        chat: ChatInternal,
        parentMessage: Message,
        threadChannelId: String,
    ): PNFuture<ThreadChannel> = notUsed()

    override fun removeChannel(channelId: String): PNFuture<Unit> = notUsed()

    override fun getMemberships(
        chat: ChatInternal,
        user: User,
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<MembershipsResponse> = notUsed()

    override fun setMembershipsWithoutStatusAndType(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse> = notUsed()

    override fun setMembershipsWithoutType(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse> = notUsed()

    override fun setMemberships(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse> = notUsed()

    override fun membershipExists(userId: String, channelId: String): PNFuture<Boolean> = notUsed()

    override fun removeMembership(userId: String, channelId: String): PNFuture<Unit> = notUsed()

    override fun removeCurrentUserMembership(channelId: String): PNFuture<Unit> = notUsed()

    override fun getMembers(
        chat: ChatInternal,
        channel: Channel,
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<MembersResponse> = notUsed()

    override fun setMembers(
        chat: ChatInternal,
        channel: Channel,
        members: Collection<MemberInput>,
        filter: String,
    ): PNFuture<List<Membership>> = notUsed()

    override fun getUserRestrictions(
        userId: String,
        filter: String,
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<GetRestrictionsResponse> = notUsed()

    override fun getChannelRestrictions(
        channelId: String,
        userId: String?,
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<GetRestrictionsResponse> = notUsed()

    override fun setRestriction(moderationChannelId: String, userId: String, custom: CustomObject): PNFuture<Unit> =
        notUsed()

    override fun removeRestriction(moderationChannelId: String, userId: String): PNFuture<Unit> = notUsed()

    override fun streamUserUpdates(
        chat: ChatInternal,
        users: Collection<User>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable = notUsed()

    override fun streamChannelUpdates(
        chat: ChatInternal,
        channels: Collection<Channel>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable = notUsed()

    override fun streamMembershipUpdates(
        chat: ChatInternal,
        memberships: Collection<Membership>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable = notUsed()
}
