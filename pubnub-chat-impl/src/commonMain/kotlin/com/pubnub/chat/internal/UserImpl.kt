package com.pubnub.chat.internal

import co.touchlab.kermit.Logger
import com.pubnub.api.PubNubException
import com.pubnub.api.endpoints.objects.uuid.SetUUIDMetadata
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.membership.MembershipInclude
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteUUIDMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetUUIDMetadataEventMessage
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
import com.pubnub.chat.internal.restrictions.RestrictionImpl
import com.pubnub.chat.internal.util.logErrorAndReturnException
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.EntityChange
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.catch
import com.pubnub.kmp.createEventListener
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

    override fun delete(soft: Boolean): PNFuture<User?> {
        return chat.deleteUser(id, soft)
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

        return chat.pubNub.getMemberships(
            userId = id,
            limit = limit,
            page = page,
            filter = effectiveFilter,
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
            MembershipsResponse(
                next = pnChannelMembershipArrayResult.next,
                prev = pnChannelMembershipArrayResult.prev,
                total = pnChannelMembershipArrayResult.totalCount ?: 0,
                status = pnChannelMembershipArrayResult.status,
                memberships = getMembershipsFromResult(pnChannelMembershipArrayResult, this)
            )
        }.catch { exception ->
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA, exception))
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
        return getRestrictions(channel).then { pnChannelMembershipArrayResult ->
            val firstMembership: PNChannelMembership = pnChannelMembershipArrayResult.data.first()
            RestrictionImpl.fromChannelMembershipDTO(id, firstMembership)
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
        ).then { pnChannelMembershipArrayResult: PNChannelMembershipArrayResult ->
            val restrictions = pnChannelMembershipArrayResult.data.map { pnChannelMembership ->
                RestrictionImpl.fromChannelMembershipDTO(id, pnChannelMembership)
            }

            GetRestrictionsResponse(
                restrictions = restrictions,
                next = pnChannelMembershipArrayResult.next,
                prev = pnChannelMembershipArrayResult.prev,
                total = pnChannelMembershipArrayResult.totalCount ?: 0,
                status = pnChannelMembershipArrayResult.status
            )
        }
    }

    override fun streamUpdates(callback: (user: User?) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) { users: Collection<User> ->
            callback(users.firstOrNull())
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
    ): PNFuture<PNChannelMembershipArrayResult> {
        val filter: String =
            if (channel != null) {
                "channel.id == '${INTERNAL_MODERATION_PREFIX}${channel.id}'"
            } else {
                "channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*'"
            }

        return chat.pubNub.getMemberships(
            userId = id,
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
        )
    }

    private fun getMembershipsFromResult(
        pnChannelMembershipArrayResult: PNChannelMembershipArrayResult,
        user: User,
    ): List<Membership> {
        val memberships: List<Membership> =
            pnChannelMembershipArrayResult.data.map { pnChannelMembership: PNChannelMembership ->
                MembershipImpl.fromMembershipDTO(chat, pnChannelMembership, user)
            }
        return memberships
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
            return user.setUserUpdatedValues(updatableValues)
                .catch {
                    if (it is PubNubException && it.statusCode == HTTP_ERROR_412) {
                        Result.success(null)
                    } else {
                        Result.failure(it)
                    }
                }.thenAsync { userDataResult: PNUUIDMetadataResult? ->
                    if (userDataResult != null) {
                        return@thenAsync fromDTO(user.chat as ChatInternal, userDataResult.data).asFuture()
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
            return streamUpdatesOnInternal(users) { _, _, latestUsers ->
                callback(latestUsers)
            }
        }

        fun streamUpdatesOnWithEntityChange(users: Collection<User>, callback: (change: EntityChange<User>) -> Unit): AutoCloseable {
            return streamUpdatesOnInternal(users) { updatedUser, deletedUserId, _ ->
                when {
                    updatedUser != null -> callback(EntityChange.Updated(updatedUser))
                    deletedUserId != null -> callback(EntityChange.Removed(deletedUserId))
                }
            }
        }

        private fun streamUpdatesOnInternal(
            users: Collection<User>,
            callback: (updatedUser: User?, deletedUserId: String?, latestUsers: Collection<User>) -> Unit
        ): AutoCloseable {
            if (users.isEmpty()) {
                log.pnError(CAN_NOT_STREAM_USER_UPDATES_ON_EMPTY_LIST)
            }
            var latestUsers = users
            val chat = users.first().chat as ChatInternal
            val listener = createEventListener(chat.pubNub, onObjects = { pubNub, event ->
                when (val message = event.extractedMessage) {
                    is PNSetUUIDMetadataEventMessage -> {
                        val newUserId = message.data.id
                        val previousUser = latestUsers.firstOrNull { it.id == newUserId }
                        val newUser = previousUser?.plus(message.data) ?: fromDTO(chat, message.data)

                        latestUsers = latestUsers.asSequence().filter { user ->
                            user.id != newUserId
                        }.plus(newUser).toList()

                        callback(newUser, null, latestUsers)
                    }
                    is PNDeleteUUIDMetadataEventMessage -> {
                        val deletedUserId = message.uuid
                        latestUsers = latestUsers.filter { user ->
                            user.id != deletedUserId
                        }

                        callback(null, deletedUserId, latestUsers)
                    }
                    else -> return@createEventListener
                }
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(users.map { it.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }
    }
}

private fun User.setUserUpdatedValues(updatableValues: User.UpdatableValues): SetUUIDMetadata = chat.pubNub.setUUIDMetadata(
    uuid = id,
    name = updatableValues.name,
    externalId = updatableValues.externalId,
    profileUrl = updatableValues.profileUrl,
    email = updatableValues.email,
    custom = updatableValues.custom,
    includeCustom = true,
    type = updatableValues.type,
    status = updatableValues.status,
    ifMatchesEtag = eTag
)

internal val User.uuidFilterString get() = "uuid.id == '${this.id}'"
internal val User.isInternalModerator get() = this.id == INTERNAL_MODERATOR_DATA_ID && this.type == INTERNAL_MODERATOR_DATA_TYPE
