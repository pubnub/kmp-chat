package com.pubnub.chat.internal.metadata

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
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

/**
 * The single boundary between Chat code and the storage of users, channels and memberships metadata.
 *
 * Every implementation turns the responses of its own storage into Chat-level types. "Not found" is
 * returned as `null`, not as a storage specific error.
 */
@Suppress("TooManyFunctions")
internal interface MetadataRepository {
    // users
    fun getUser(chat: ChatInternal, userId: String): PNFuture<User?>

    fun getUsers(
        chat: ChatInternal,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetUsersResponse>

    fun setUser(
        chat: ChatInternal,
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        type: String?,
        status: String?,
    ): PNFuture<User>

    /**
     * Updates [user] with [values], only if the stored user still has the same version as [user]
     * (the call fails with HTTP 412 otherwise).
     */
    fun updateUser(chat: ChatInternal, user: User, values: User.UpdatableValues): PNFuture<User>

    fun removeUser(userId: String): PNFuture<Unit>

    // mute list of a user
    fun getMutedUsers(mutedUsersStorageId: String): PNFuture<Set<String>>

    fun setMutedUsers(mutedUsersStorageId: String, ownerUserId: String, mutedUsers: Set<String>): PNFuture<Unit>

    /**
     * Calls [callback] with the full mute list every time it changes (an empty set when it is removed).
     * It doesn't subscribe to anything, so the events arrive only while the caller keeps
     * [mutedUsersStorageId] subscribed.
     */
    fun streamMutedUsers(mutedUsersStorageId: String, callback: (Set<String>) -> Unit): AutoCloseable

    // channels
    fun getChannel(chat: ChatInternal, channelId: String): PNFuture<Channel?>

    fun getChannels(
        chat: ChatInternal,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetChannelsResponse>

    fun setChannel(
        chat: ChatInternal,
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?,
    ): PNFuture<Channel>

    /** Replaces only the custom data of the channel. Used for pinning and unpinning messages. */
    fun setChannelCustom(chat: ChatInternal, channelId: String, custom: CustomObject): PNFuture<Channel>

    /** Stores the description of the thread channel. */
    fun setThreadChannelDescription(threadChannelId: String, description: String?): PNFuture<Unit>

    /** Fails when the thread channel doesn't exist. */
    fun getThreadChannel(chat: ChatInternal, parentMessage: Message, threadChannelId: String): PNFuture<ThreadChannel>

    fun removeChannel(channelId: String): PNFuture<Unit>

    // memberships
    fun getMemberships(
        chat: ChatInternal,
        user: User,
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<MembershipsResponse>

    /** Sets memberships of [user], the result has no `status` and `type`. */
    fun setMembershipsWithoutStatusAndType(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse>

    /** Sets memberships of [user], the result has `status` but no `type`. */
    fun setMembershipsWithoutType(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse>

    /** Sets memberships of [user], the result has all fields. */
    fun setMemberships(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse>

    fun membershipExists(userId: String, channelId: String): PNFuture<Boolean>

    fun removeMembership(userId: String, channelId: String): PNFuture<Unit>

    /** Removes the membership of the user the Chat was created for. */
    fun removeCurrentUserMembership(channelId: String): PNFuture<Unit>

    // channel members
    fun getMembers(
        chat: ChatInternal,
        channel: Channel,
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<MembersResponse>

    fun setMembers(
        chat: ChatInternal,
        channel: Channel,
        members: Collection<MemberInput>,
        filter: String,
    ): PNFuture<List<Membership>>

    // restrictions (moderation memberships)
    fun getUserRestrictions(
        userId: String,
        filter: String,
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<GetRestrictionsResponse>

    fun getChannelRestrictions(
        channelId: String,
        userId: String?,
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<GetRestrictionsResponse>

    fun setRestriction(moderationChannelId: String, userId: String, custom: CustomObject): PNFuture<Unit>

    fun removeRestriction(moderationChannelId: String, userId: String): PNFuture<Unit>

    // events (subscribed per entity; the implementation merges patches into the latest state it was given)
    fun streamUserUpdates(chat: ChatInternal, users: Collection<User>, callback: (MetadataEvent) -> Unit): AutoCloseable

    fun streamChannelUpdates(
        chat: ChatInternal,
        channels: Collection<Channel>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable

    fun streamMembershipUpdates(
        chat: ChatInternal,
        memberships: Collection<Membership>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable
}
