package com.pubnub.chat.internal

import co.touchlab.kermit.Logger
import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.utils.Clock
import com.pubnub.api.utils.Instant
import com.pubnub.api.utils.PatchValue
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.User
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.internal.error.PubNubErrorMessage.CAN_NOT_STREAM_USER_UPDATES_ON_EMPTY_LIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_USER_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY
import com.pubnub.chat.internal.error.PubNubErrorMessage.USER_NOT_EXIST
import com.pubnub.chat.internal.metadata.MetadataEvent
import com.pubnub.chat.internal.util.logErrorAndReturnException
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.listenForEvents
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.restrictions.RestrictionType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.user.Invite
import com.pubnub.chat.user.Mention
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.catch
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import tryLong

data class UserImpl(
    override val chat: ChatInternal,
    override val id: String,
    override val name: String? = null,
    override val externalId: String? = null,
    override val profileUrl: String? = null,
    override val email: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val status: String? = null,
    override val type: String? = null,
    override val updated: String? = null,
    override val eTag: String? = null,
    override val lastActiveTimestamp: Long? = null,
) : User {
    override val active: Boolean
        get() {
            return lastActiveTimestamp?.let { lastActiveTimestampNonNull ->
                Clock.System.now() - Instant.fromEpochMilliseconds(lastActiveTimestampNonNull) <= chat.config.storeUserActivityInterval
            } ?: false
        }

    override fun update(
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?,
    ): PNFuture<User> {
        return chat.updateUser(
            id,
            name,
            externalId,
            profileUrl,
            email,
            custom,
            status,
            type
        )
    }

    override fun update(
        updateAction: User.UpdatableValues.(
            user: User
        ) -> Unit
    ): PNFuture<User> {
        return updateInternal(this, updateAction)
    }

    override fun delete(): PNFuture<Unit> {
        return chat.deleteUser(id)
    }

    override fun wherePresent(): PNFuture<List<String>> {
        return chat.wherePresent(id)
    }

    override fun isPresentOn(channelId: String): PNFuture<Boolean> {
        return chat.isPresent(id, channelId)
    }

    override fun getMemberships(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<MembershipsResponse> {
        val internalModerationFilter = "!(channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*')"
        val effectiveFilter: String = filter?.let { "$internalModerationFilter && ($filter)" } ?: internalModerationFilter

        return chat.metadataRepository.getMemberships(
            chat = chat,
            user = this,
            limit = limit,
            page = page,
            filter = effectiveFilter,
            sort = sort,
        ).catch { exception ->
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA, exception))
        }
    }

    override fun isMemberOf(channelId: String): PNFuture<Boolean> {
        return getMemberships(filter = "channel.id == '$channelId'", limit = 1).then { membershipsResponse ->
            membershipsResponse.memberships.isNotEmpty()
        }
    }

    override fun getMembership(channelId: String): PNFuture<Membership?> {
        return getMemberships(filter = "channel.id == '$channelId'", limit = 1).then { membershipsResponse ->
            membershipsResponse.memberships.firstOrNull()
        }
    }

    override fun setRestrictions(channel: Channel, ban: Boolean, mute: Boolean, reason: String?): PNFuture<Unit> {
        if (chat.pubNub.configuration.secretKey.isEmpty()) {
            return log.logErrorAndReturnException(MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY).asFuture()
        }
        return chat.setRestrictions(
            Restriction(
                userId = id,
                channelId = channel.id,
                ban = ban,
                mute = mute,
                reason = reason
            )
        )
    }

    override fun getChannelRestrictions(channel: Channel): PNFuture<Restriction> {
        return getRestrictions(channel).then { response ->
            response.restrictions.first()
        }
    }

    override fun getChannelsRestrictions(
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<GetRestrictionsResponse> {
        val undefinedChannel = null

        return getRestrictions(
            channel = undefinedChannel,
            limit = limit,
            page = page,
            sort = sort
        )
    }

    override fun onUpdated(callback: (user: User) -> Unit): AutoCloseable {
        return chat.metadataRepository.streamUserUpdates(chat, listOf(this)) { event ->
            if (event is MetadataEvent.UserUpdated && event.user.id == id) {
                callback(event.user)
            }
        }
    }

    override fun onDeleted(callback: () -> Unit): AutoCloseable {
        return chat.metadataRepository.streamUserUpdates(chat, listOf(this)) { event ->
            if (event is MetadataEvent.UserDeleted && event.userId == id) {
                callback()
            }
        }
    }

    override fun onMentioned(callback: (mention: Mention) -> Unit): AutoCloseable {
        return chat.listenForEvents<EventContent.Mention>(channelId = id) { event ->
            callback(
                Mention(
                    messageTimetoken = event.payload.messageTimetoken,
                    channelId = event.payload.channel,
                    parentChannelId = event.payload.parentChannel,
                    mentionedByUserId = event.userId,
                )
            )
        }
    }

    override fun onInvited(callback: (invite: Invite) -> Unit): AutoCloseable {
        return chat.listenForEvents<EventContent.Invite>(channelId = id) { event ->
            callback(
                Invite(
                    channelId = event.payload.channelId,
                    channelType = event.payload.channelType,
                    invitedByUserId = event.userId,
                    invitationTimetoken = event.timetoken,
                )
            )
        }
    }

    override fun onRestrictionChanged(callback: (restriction: Restriction) -> Unit): AutoCloseable {
        return chat.listenForEvents<EventContent.Moderation>(channelId = INTERNAL_USER_MODERATION_CHANNEL_PREFIX + id) { event ->
            callback(
                Restriction(
                    userId = id,
                    channelId = event.payload.channelId.removePrefix(INTERNAL_MODERATION_PREFIX),
                    ban = event.payload.restriction == RestrictionType.BAN,
                    mute = event.payload.restriction == RestrictionType.MUTE,
                    reason = event.payload.reason,
                )
            )
        }
    }

    @Deprecated("Use onUpdated() and onDeleted() instead.", ReplaceWith("onUpdated(callback)"))
    override fun streamUpdates(callback: (user: User?) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) {
            callback(it.firstOrNull())
        }
    }

    @Deprecated("Use non-async `active` property instead.", replaceWith = ReplaceWith("active"))
    override fun active(): PNFuture<Boolean> = active.asFuture()

    override operator fun plus(update: PNUUIDMetadata): User {
        return fromDTO(chat, toUUIDMetadata() + update)
    }

    internal fun getRestrictions(
        channel: Channel?,
        limit: Int? = null,
        page: PNPage? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<GetRestrictionsResponse> {
        val filter: String =
            if (channel != null) {
                "channel.id == '${INTERNAL_MODERATION_PREFIX}${channel.id}'"
            } else {
                "channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*'"
            }

        return chat.metadataRepository.getUserRestrictions(
            userId = id,
            filter = filter,
            limit = limit,
            page = page,
            sort = sort,
        )
    }

    private fun toUUIDMetadata(): PNUUIDMetadata {
        return PNUUIDMetadata(
            id = id,
            name = name?.let { PatchValue.of(it) },
            externalId = externalId?.let { PatchValue.of(it) },
            profileUrl = profileUrl?.let { PatchValue.of(it) },
            email = email?.let { PatchValue.of(it) },
            custom = custom?.let { PatchValue.of(it) },
            updated = updated?.let { PatchValue.of(it) },
            eTag = null,
            type = type?.let { PatchValue.of(it) },
            status = status?.let { PatchValue.of(it) },
        )
    }

    companion object {
        private val log = Logger.withTag("UserImpl")

        private fun updateInternal(
            user: User,
            updateAction: User.UpdatableValues.(
                user: User
            ) -> Unit,
            retriesLeft: Int = 1
        ): PNFuture<User> {
            val updatableValues = User.UpdatableValues()
            updatableValues.updateAction(user)
            val chat = user.chat as ChatInternal
            return chat.metadataRepository.updateUser(chat, user, updatableValues)
                .catch {
                    if (it is PubNubException && it.statusCode == HTTP_ERROR_412) {
                        Result.success(null)
                    } else {
                        Result.failure(it)
                    }
                }.thenAsync { updatedUser: User? ->
                    if (updatedUser != null) {
                        return@thenAsync updatedUser.asFuture()
                    }
                    user.chat.getUser(user.id).thenAsync { newUser: User? ->
                        if (newUser == null) {
                            log.pnError(USER_NOT_EXIST)
                        } else if (retriesLeft > 0) {
                            updateInternal(newUser, updateAction, retriesLeft - 1)
                        } else {
                            log.pnError(FAILED_TO_CREATE_UPDATE_USER_DATA)
                        }
                    }
                }
        }

        internal fun fromDTO(chat: ChatInternal, user: PNUUIDMetadata): User = UserImpl(
            chat,
            id = user.id,
            name = user.name?.value,
            externalId = user.externalId?.value,
            profileUrl = user.profileUrl?.value,
            email = user.email?.value,
            custom = user.custom?.value,
            updated = user.updated?.value,
            status = user.status?.value,
            type = user.type?.value,
            eTag = user.eTag?.value,
            lastActiveTimestamp = user.custom?.value?.get(LAST_ACTIVE_TIMESTAMP)?.tryLong()
        )

        fun streamUpdatesOn(users: Collection<User>, callback: (users: Collection<User>) -> Unit): AutoCloseable {
            if (users.isEmpty()) {
                log.pnError(CAN_NOT_STREAM_USER_UPDATES_ON_EMPTY_LIST)
            }
            var latestUsers = users
            val chat = users.first().chat as ChatInternal
            return chat.metadataRepository.streamUserUpdates(chat, users) { event ->
                val (newUser, newUserId) = when (event) {
                    is MetadataEvent.UserUpdated -> event.user to event.user.id
                    is MetadataEvent.UserDeleted -> null to event.userId
                    else -> return@streamUserUpdates
                }

                latestUsers = latestUsers.asSequence().filter { user ->
                    user.id != newUserId
                }.let { sequence ->
                    if (newUser != null) {
                        sequence + newUser
                    } else {
                        sequence
                    }
                }.toList()
                callback(latestUsers)
            }
        }
    }
}

internal val User.uuidFilterString get() = "uuid.id == '${this.id}'"
internal val User.isInternalModerator get() = this.id == INTERNAL_MODERATOR_DATA_ID && this.type == INTERNAL_MODERATOR_DATA_TYPE
