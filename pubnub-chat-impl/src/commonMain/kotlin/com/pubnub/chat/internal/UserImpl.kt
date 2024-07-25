package com.pubnub.chat.internal

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteUUIDMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetUUIDMetadataEventMessage
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.User
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.internal.restrictions.RestrictionImpl
import com.pubnub.chat.membership.IncludeParameters
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.catch
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.then
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    override val lastActiveTimestamp: Long? = null,
) : User {
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

    override fun delete(soft: Boolean): PNFuture<User> {
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
        val includeParameters = IncludeParameters()

        return chat.pubNub.getMemberships(
            uuid = id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = includeParameters.totalCount,
            includeCustom = includeParameters.customFields,
            includeChannelDetails = getChannelDetailsType(includeParameters.customChannelFields)
        ).then { pnChannelMembershipArrayResult ->
            MembershipsResponse(
                next = pnChannelMembershipArrayResult.next,
                prev = pnChannelMembershipArrayResult.prev,
                total = pnChannelMembershipArrayResult.totalCount ?: 0,
                status = pnChannelMembershipArrayResult.status,
                memberships = getMembershipsFromResult(pnChannelMembershipArrayResult, this).toSet()
            )
        }.catch { exception ->
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA, exception))
        }
    }

    override fun setRestrictions(channel: Channel, ban: Boolean, mute: Boolean, reason: String?): PNFuture<Unit> {
        if (chat.pubNub.configuration.secretKey.isEmpty()) {
            throw PubNubException(PubNubErrorMessage.MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY)
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
            }.toSet()

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
        return streamUpdatesOn(listOf(this)) {
            callback(it.firstOrNull())
        }
    }

    override fun active(): PNFuture<Boolean> {
        if (!chat.config.storeUserActivityTimestamps) {
            throw PubNubException(PubNubErrorMessage.STORE_USER_ACTIVITY_INTERVAL_IS_FALSE)
        }
        return (
            lastActiveTimestamp?.let { lastActiveTimestampNonNull ->
                Clock.System.now() - Instant.fromEpochMilliseconds(lastActiveTimestampNonNull) <= chat.config.storeUserActivityInterval
            } ?: false
        ).asFuture()
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
            uuid = id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true,
            includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
            includeType = true
        )
    }

    private fun getChannelDetailsType(includeChannelWithCustom: Boolean): PNChannelDetailsLevel {
        return if (includeChannelWithCustom) {
            PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM
        } else {
            PNChannelDetailsLevel.CHANNEL
        }
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

    companion object {
        internal fun fromDTO(chat: ChatInternal, user: PNUUIDMetadata): User = UserImpl(
            chat,
            id = user.id,
            name = user.name,
            externalId = user.externalId,
            profileUrl = user.profileUrl,
            email = user.email,
            custom = user.custom,
            updated = user.updated,
            status = user.status,
            type = user.type,
            lastActiveTimestamp = user.custom?.get("lastActiveTimestamp")?.tryLong()
        )

        fun streamUpdatesOn(users: Collection<User>, callback: (users: Collection<User>) -> Unit): AutoCloseable {
            if (users.isEmpty()) {
                throw PubNubException("Cannot stream user updates on an empty list")
            }
            var latestUsers = users
            val chat = users.first().chat as ChatInternal
            val listener = createEventListener(chat.pubNub, onObjects = { pubNub, event ->
                val (newUser, newUserId) = when (val message = event.extractedMessage) {
                    is PNSetUUIDMetadataEventMessage -> fromDTO(chat, message.data) to message.data.id
                    is PNDeleteUUIDMetadataEventMessage -> null to message.uuid
                    else -> return@createEventListener
                }

                latestUsers = latestUsers.asSequence().filter {
                    it.id != newUserId
                }.run { newUser?.let { plus(it) } ?: this }.toList()
                callback(latestUsers)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(users.map { it.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }
    }
}

internal val User.uuidFilterString get() = "uuid.id == '${this.id}'"
