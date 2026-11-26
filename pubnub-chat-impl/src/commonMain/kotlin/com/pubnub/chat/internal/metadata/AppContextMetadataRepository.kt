package com.pubnub.chat.internal.metadata

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.member.MemberInclude
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.membership.MembershipInclude
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteChannelMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteMembershipEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteUUIDMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNObjectEventResult
import com.pubnub.api.models.consumer.pubsub.objects.PNSetChannelMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetUUIDMetadataEventMessage
import com.pubnub.api.utils.PatchValue
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.TYPE_PUBNUB_PRIVATE
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.internal.restrictions.RestrictionImpl
import com.pubnub.chat.internal.util.nullOn404
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.remember
import com.pubnub.kmp.then

/**
 * [MetadataRepository] that keeps the metadata in AppContext of the PubNub Core SDK.
 */
internal class AppContextMetadataRepository(private val pubNub: PubNub) : MetadataRepository {
    // users
    override fun getUser(chat: ChatInternal, userId: String): PNFuture<User?> {
        return pubNub.getUUIDMetadata(uuid = userId, includeCustom = true)
            .then { pnUUIDMetadataResult: PNUUIDMetadataResult ->
                UserImpl.fromDTO(chat, pnUUIDMetadataResult.data)
            }.nullOn404()
    }

    override fun getUsers(
        chat: ChatInternal,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetUsersResponse> {
        return pubNub.getAllUUIDMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).then { pnUUIDMetadataArrayResult: PNUUIDMetadataArrayResult ->
            val users = pnUUIDMetadataArrayResult.data.map { pnUUIDMetadata ->
                UserImpl.fromDTO(chat, pnUUIDMetadata)
            }
            GetUsersResponse(
                users = users,
                next = pnUUIDMetadataArrayResult.next,
                prev = pnUUIDMetadataArrayResult.prev,
                total = pnUUIDMetadataArrayResult.totalCount ?: 0
            )
        }
    }

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
    ): PNFuture<User> {
        return pubNub.setUUIDMetadata(
            uuid = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            includeCustom = true,
            type = type,
            status = status
        ).then { pnUUIDMetadataResult ->
            UserImpl.fromDTO(chat, pnUUIDMetadataResult.data)
        }
    }

    override fun updateUser(chat: ChatInternal, user: User, values: User.UpdatableValues): PNFuture<User> {
        return pubNub.setUUIDMetadata(
            uuid = user.id,
            name = values.name,
            externalId = values.externalId,
            profileUrl = values.profileUrl,
            email = values.email,
            custom = values.custom,
            includeCustom = true,
            type = values.type,
            status = values.status,
            ifMatchesEtag = user.eTag
        ).then { pnUUIDMetadataResult ->
            UserImpl.fromDTO(chat, pnUUIDMetadataResult.data)
        }
    }

    override fun removeUser(userId: String): PNFuture<Unit> =
        pubNub.removeUUIDMetadata(uuid = userId).then { }

    // mute list of a user
    override fun getMutedUsers(mutedUsersStorageId: String): PNFuture<Set<String>> {
        return pubNub.getUUIDMetadata(
            mutedUsersStorageId,
            includeCustom = true
        ).nullOn404().then {
            customToMutedUsersSet(it?.data?.custom)
        }
    }

    override fun setMutedUsers(
        mutedUsersStorageId: String,
        ownerUserId: String,
        mutedUsers: Set<String>,
    ): PNFuture<Unit> {
        return pubNub.setUUIDMetadata(
            name = ownerUserId,
            uuid = mutedUsersStorageId,
            includeCustom = false,
            type = TYPE_PUBNUB_PRIVATE,
            custom = mutedUsersSetToCustom(mutedUsers)
        ).then { Unit }.remember()
    }

    override fun streamMutedUsers(mutedUsersStorageId: String, callback: (Set<String>) -> Unit): AutoCloseable {
        val listener = createEventListener(
            pubNub,
            onObjects = { _, objectEvent ->
                when (val message = objectEvent.extractedMessage) {
                    is PNSetUUIDMetadataEventMessage -> {
                        if (message.data.id == mutedUsersStorageId) {
                            callback(customToMutedUsersSet(message.data.custom))
                        }
                    }

                    is PNDeleteUUIDMetadataEventMessage -> {
                        if (message.uuid == mutedUsersStorageId) {
                            callback(emptySet())
                        }
                    }

                    else -> {}
                }
            }
        )
        pubNub.addListener(listener)
        return AutoCloseable { pubNub.removeListener(listener) }
    }

    // channels
    override fun getChannel(chat: ChatInternal, channelId: String): PNFuture<Channel?> {
        return pubNub.getChannelMetadata(channel = channelId, includeCustom = true)
            .then { pnChannelMetadataResult ->
                ChannelImpl.fromDTO(chat, pnChannelMetadataResult.data)
            }.nullOn404()
    }

    override fun getChannels(
        chat: ChatInternal,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetChannelsResponse> {
        return pubNub.getAllChannelMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).then { pnChannelMetadataArrayResult ->
            val channels = pnChannelMetadataArrayResult.data.map { pnChannelMetadata ->
                ChannelImpl.fromDTO(chat, pnChannelMetadata)
            }
            GetChannelsResponse(
                channels = channels,
                next = pnChannelMetadataArrayResult.next,
                prev = pnChannelMetadataArrayResult.prev,
                total = pnChannelMetadataArrayResult.totalCount ?: 0
            )
        }
    }

    override fun setChannel(
        chat: ChatInternal,
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?,
    ): PNFuture<Channel> {
        return pubNub.setChannelMetadata(
            channel = id,
            name = name,
            description = description,
            custom = custom,
            includeCustom = true,
            type = type?.stringValue,
            status = status
        ).then { pnChannelMetadataResult ->
            ChannelImpl.fromDTO(chat, pnChannelMetadataResult.data)
        }
    }

    override fun setChannelCustom(chat: ChatInternal, channelId: String, custom: CustomObject): PNFuture<Channel> {
        return pubNub.setChannelMetadata(
            channel = channelId,
            includeCustom = true,
            custom = custom
        ).then { pnChannelMetadataResult ->
            ChannelImpl.fromDTO(chat, pnChannelMetadataResult.data)
        }
    }

    override fun setThreadChannelDescription(threadChannelId: String, description: String?): PNFuture<Unit> {
        return pubNub.setChannelMetadata(threadChannelId, description = description).then { }
    }

    override fun getThreadChannel(
        chat: ChatInternal,
        parentMessage: Message,
        threadChannelId: String,
    ): PNFuture<ThreadChannel> {
        return pubNub.getChannelMetadata(threadChannelId).then {
            ThreadChannelImpl.fromDTO(chat, parentMessage, it.data)
        }
    }

    override fun removeChannel(channelId: String): PNFuture<Unit> =
        pubNub.removeChannelMetadata(channel = channelId).then { }

    // memberships
    override fun getMemberships(
        chat: ChatInternal,
        user: User,
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<MembershipsResponse> {
        return pubNub.getMemberships(
            userId = user.id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            include = MembershipInclude(
                includeCustom = true,
                includeStatus = true,
                includeType = true,
                includeTotalCount = true,
                includeChannel = true,
                includeChannelCustom = true,
                includeChannelType = true,
                includeChannelStatus = true
            )
        ).then { pnChannelMembershipArrayResult ->
            pnChannelMembershipArrayResult.toMembershipsResponse(chat, user)
        }
    }

    override fun setMembershipsWithoutStatusAndType(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse> {
        return pubNub.setMemberships(
            channels = memberships.map { it.toPartial() },
            userId = user.id,
            filter = filter,
            include = MembershipInclude(
                includeCustom = true,
                includeStatus = false,
                includeType = false,
                includeTotalCount = true,
                includeChannel = true,
                includeChannelCustom = true,
                includeChannelType = true,
                includeChannelStatus = false
            ),
        ).then { it.toMembershipsResponse(chat, user) }
    }

    override fun setMembershipsWithoutType(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse> {
        return pubNub.setMemberships(
            channels = memberships.map { it.toPartial() },
            userId = user.id,
            filter = filter,
            include = MembershipInclude(
                includeCustom = true,
                includeStatus = true,
                includeType = false,
                includeTotalCount = true,
                includeChannel = true,
                includeChannelCustom = true,
                includeChannelType = true,
                includeChannelStatus = false
            )
        ).then { it.toMembershipsResponse(chat, user) }
    }

    override fun setMemberships(
        chat: ChatInternal,
        user: User,
        memberships: Collection<MembershipInput>,
        filter: String,
    ): PNFuture<MembershipsResponse> {
        return pubNub.setMemberships(
            channels = memberships.map { it.toPartial() },
            userId = user.id,
            filter = filter,
            include = MembershipInclude(
                includeCustom = true,
                includeStatus = true,
                includeType = true,
                includeTotalCount = true,
                includeChannel = true,
                includeChannelCustom = true,
                includeChannelType = true,
                includeChannelStatus = true
            )
        ).then { it.toMembershipsResponse(chat, user) }
    }

    override fun membershipExists(userId: String, channelId: String): PNFuture<Boolean> {
        return pubNub.getMemberships(userId = userId, filter = "channel.id == '$channelId'").then {
            it.data.isNotEmpty()
        }
    }

    override fun removeMembership(userId: String, channelId: String): PNFuture<Unit> {
        return pubNub.removeMemberships(
            userId = userId,
            channels = listOf(channelId)
        ).then { Unit }
    }

    override fun removeCurrentUserMembership(channelId: String): PNFuture<Unit> =
        pubNub.removeMemberships(channels = listOf(channelId), include = MembershipInclude()).then { Unit }

    // channel members
    override fun getMembers(
        chat: ChatInternal,
        channel: Channel,
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<MembersResponse> {
        return pubNub.getChannelMembers(
            channel = channel.id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            include = MemberInclude(
                includeCustom = true,
                includeStatus = true,
                includeType = true,
                includeTotalCount = true,
                includeUser = true,
                includeUserCustom = true,
                includeUserType = true,
                includeUserStatus = false
            ),
        ).then { result: PNMemberArrayResult ->
            MembersResponse(
                result.next,
                result.prev,
                result.totalCount!!,
                result.status,
                result.data.map { member ->
                    MembershipImpl.fromChannelMemberDTO(chat, member, channel)
                }
            )
        }
    }

    override fun setMembers(
        chat: ChatInternal,
        channel: Channel,
        members: Collection<MemberInput>,
        filter: String,
    ): PNFuture<List<Membership>> {
        return pubNub.setChannelMembers(
            channel.id,
            members.map { PNMember.Partial(uuidId = it.userId, status = it.status) },
            include = MemberInclude(
                includeCustom = true,
                includeStatus = true,
                includeType = false,
                includeTotalCount = true,
                includeUser = true,
                includeUserCustom = true,
                includeUserType = true,
                includeUserStatus = false
            ),
            filter = filter
        ).then { memberArrayResult: PNMemberArrayResult ->
            memberArrayResult.data.map { MembershipImpl.fromChannelMemberDTO(chat, it, channel) }
        }
    }

    // restrictions
    override fun getUserRestrictions(
        userId: String,
        filter: String,
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<GetRestrictionsResponse> {
        return pubNub.getMemberships(
            userId = userId,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            include = MembershipInclude(
                includeCustom = true,
                includeStatus = true,
                includeType = true,
                includeTotalCount = true,
                includeChannel = true,
                includeChannelCustom = true,
                includeChannelType = true,
                includeChannelStatus = true
            )
        ).then { pnChannelMembershipArrayResult: PNChannelMembershipArrayResult ->
            GetRestrictionsResponse(
                restrictions = pnChannelMembershipArrayResult.data.map { pnChannelMembership ->
                    RestrictionImpl.fromChannelMembershipDTO(userId, pnChannelMembership)
                },
                next = pnChannelMembershipArrayResult.next,
                prev = pnChannelMembershipArrayResult.prev,
                total = pnChannelMembershipArrayResult.totalCount ?: 0,
                status = pnChannelMembershipArrayResult.status
            )
        }
    }

    override fun getChannelRestrictions(
        channelId: String,
        userId: String?,
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<GetRestrictionsResponse> {
        return pubNub.getChannelMembers(
            channel = "$INTERNAL_MODERATION_PREFIX$channelId",
            limit = limit,
            page = page,
            filter = userId?.let { "uuid.id == '$userId'" },
            sort = sort,
            include = MemberInclude(
                includeCustom = true,
                includeStatus = false,
                includeType = false,
                includeTotalCount = true,
                includeUser = true,
                includeUserCustom = true,
                includeUserType = true,
                includeUserStatus = false
            ),
        ).then { pnMemberArrayResult: PNMemberArrayResult ->
            GetRestrictionsResponse(
                restrictions = pnMemberArrayResult.data.map { pnMember ->
                    RestrictionImpl.fromMemberDTO(channelId, pnMember)
                },
                next = pnMemberArrayResult.next,
                prev = pnMemberArrayResult.prev,
                total = pnMemberArrayResult.totalCount ?: 0,
                status = pnMemberArrayResult.status
            )
        }
    }

    override fun setRestriction(moderationChannelId: String, userId: String, custom: CustomObject): PNFuture<Unit> {
        val members = listOf(PNMember.Partial(uuidId = userId, custom = custom, null))
        return pubNub.setChannelMembers(channel = moderationChannelId, users = members).then { }
    }

    override fun removeRestriction(moderationChannelId: String, userId: String): PNFuture<Unit> {
        return pubNub.removeChannelMembers(channel = moderationChannelId, uuids = listOf(userId)).then { }
    }

    // events
    override fun streamUserUpdates(
        chat: ChatInternal,
        users: Collection<User>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable {
        val tracker = UserEventTracker(chat, users)
        return subscribe(users.map { it.id }.toSet()) { event ->
            val metadataEvent = when (val message = event.extractedMessage) {
                is PNSetUUIDMetadataEventMessage -> tracker.updated(message.data)
                is PNDeleteUUIDMetadataEventMessage -> tracker.deleted(message.uuid)
                else -> null
            }
            metadataEvent?.let(callback)
        }
    }

    override fun streamChannelUpdates(
        chat: ChatInternal,
        channels: Collection<Channel>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable {
        val tracker = ChannelEventTracker(chat, channels)
        return subscribe(channels.map { it.id }.toSet()) { event ->
            val metadataEvent = when (val message = event.extractedMessage) {
                is PNSetChannelMetadataEventMessage -> tracker.updated(message.data)
                is PNDeleteChannelMetadataEventMessage -> tracker.deleted(message.channel)
                else -> null
            }
            metadataEvent?.let(callback)
        }
    }

    override fun streamMembershipUpdates(
        chat: ChatInternal,
        memberships: Collection<Membership>,
        callback: (MetadataEvent) -> Unit,
    ): AutoCloseable {
        val tracker = MembershipEventTracker(chat, memberships)
        return subscribe(memberships.map { it.channel.id }.toSet()) { event ->
            val metadataEvent = when (val message = event.extractedMessage) {
                is PNSetMembershipEventMessage -> tracker.updated(event.channel, message.data)
                is PNDeleteMembershipEventMessage -> tracker.deleted(event.channel, message.data.uuid)
                else -> null
            }
            metadataEvent?.let(callback)
        }
    }

    private fun subscribe(channelIds: Set<String>, onObjects: (PNObjectEventResult) -> Unit): AutoCloseable {
        val listener = createEventListener(pubNub, onObjects = { _, event -> onObjects(event) })
        val subscriptionSet = pubNub.subscriptionSetOf(channelIds)
        subscriptionSet.addListener(listener)
        subscriptionSet.subscribe()
        return subscriptionSet
    }

    private fun MembershipInput.toPartial() = PNChannelMembership.Partial(
        channelId = channelId,
        custom = custom,
        status = status,
        type = type
    )

    private fun PNChannelMembershipArrayResult.toMembershipsResponse(chat: ChatInternal, user: User) =
        MembershipsResponse(
            next = next,
            prev = prev,
            total = totalCount ?: 0,
            status = status,
            memberships = data.map { MembershipImpl.fromMembershipDTO(chat, it, user) }
        )

    private fun customToMutedUsersSet(custom: PatchValue<Map<String, Any?>?>?): Set<String> {
        val mutedUsersList = custom?.value?.getOrElse("m") { "" } as? String
        return mutedUsersList?.split(",")?.filterNot { it.isEmpty() }?.toSet() ?: emptySet()
    }

    private fun mutedUsersSetToCustom(set: Set<String>): CustomObject {
        return createCustomObject(mapOf("m" to set.joinToString(",")))
    }
}
