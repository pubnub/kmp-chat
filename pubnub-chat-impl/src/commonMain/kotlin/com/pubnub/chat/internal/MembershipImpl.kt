package com.pubnub.chat.internal

import co.touchlab.kermit.Logger
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEvent
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage.CAN_NOT_STREAM_MEMBERSHIP_UPDATES_ON_EMPTY_LIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.NO_SUCH_MEMBERSHIP_EXISTS
import com.pubnub.chat.internal.error.PubNubErrorMessage.RECEIPT_EVENT_WAS_NOT_SENT_TO_CHANNEL
import com.pubnub.chat.internal.metadata.MembershipInput
import com.pubnub.chat.internal.metadata.MetadataEvent
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.internal.utils.AccessManager
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import tryLong

data class MembershipImpl(
    override val chat: ChatInternal,
    override val channel: Channel,
    override val user: User,
    override val custom: Map<String, Any?>?,
    override val updated: String?,
    override val eTag: String?,
    override val status: String?,
    override val type: String?,
) : Membership {
    override val lastReadMessageTimetoken: Long?
        get() {
            return custom?.get(METADATA_LAST_READ_MESSAGE_TIMETOKEN).tryLong()
        }

    override fun setLastReadMessage(message: Message): PNFuture<Membership> {
        return setLastReadMessageTimetoken(message.timetoken)
    }

    override fun delete(): PNFuture<Unit> {
        return chat.metadataRepository.removeMembership(userId = user.id, channelId = channel.id)
    }

    override fun update(status: String?, type: String?, custom: CustomObject?): PNFuture<Membership> {
        return exists().thenAsync { exists ->
            if (!exists) {
                log.pnError(NO_SUCH_MEMBERSHIP_EXISTS)
            }
            chat.metadataRepository.setMemberships(
                chat = chat,
                user = user,
                memberships = listOf(
                    MembershipInput(
                        channelId = channel.id,
                        custom = mergeCustomWithLastReadTimetoken(custom),
                        status = status,
                        type = type,
                    )
                ),
                filter = filterThisChannel()
            ).then { membershipsResponse ->
                membershipsResponse.memberships.first()
            }
        }
    }

    override fun setLastReadMessageTimetoken(timetoken: Long): PNFuture<Membership> {
        val newCustom = buildMap {
            custom?.let { putAll(it) }
            // toString is required because server for odd numbers larger than 9007199254740991(timetoken has 17 digits)
            // returns values that differ by one
            put(METADATA_LAST_READ_MESSAGE_TIMETOKEN, timetoken.toString())
        }
        return update(custom = createCustomObject(newCustom)).alsoAsync {
            val shouldEmitReadReceiptEvent = channel.type?.let { chat.config.emitReadReceiptEvents[it] } != false

            if (!shouldEmitReadReceiptEvent) {
                return@alsoAsync Unit.asFuture()
            }
            val canISendSignal = AccessManager(chat).canI(
                AccessManager.Permission.WRITE,
                AccessManager.ResourceType.CHANNELS,
                channel.id
            )
            if (canISendSignal) {
                chat.emitEvent(channel.id, EventContent.Receipt(timetoken))
            } else {
                log.w("$RECEIPT_EVENT_WAS_NOT_SENT_TO_CHANNEL${this.channel.id}")
                Unit.asFuture()
            }
        }
    }

    override fun getUnreadMessagesCount(): PNFuture<Long?> {
        return lastReadMessageTimetoken?.let { timetoken ->
            chat.pubNub.messageCounts(
                channels = listOf(channel.id),
                channelsTimetoken = listOf(timetoken)
            ).then { pnMessageCountResult ->
                pnMessageCountResult.channels[channel.id]!!
            }
        } ?: (null as Long?).asFuture()
    }

    override fun streamUpdates(callback: (membership: Membership?) -> Unit): AutoCloseable {
        val onUpdatedCloseable = onUpdated { membership -> callback(membership) }
        val onDeletedCloseable = onDeleted { callback(null) }
        return AutoCloseable {
            onUpdatedCloseable.close()
            onDeletedCloseable.close()
        }
    }

    override fun onUpdated(callback: (membership: Membership) -> Unit): AutoCloseable {
        return chat.metadataRepository.streamMembershipUpdates(chat, listOf(this)) { event ->
            if (event is MetadataEvent.MembershipUpdated &&
                event.membership.channel.id == channel.id &&
                event.membership.user.id == user.id
            ) {
                callback(event.membership)
            }
        }
    }

    override fun onDeleted(callback: () -> Unit): AutoCloseable {
        return chat.metadataRepository.streamMembershipUpdates(chat, listOf(this)) { event ->
            if (event is MetadataEvent.MembershipDeleted && event.channelId == channel.id && event.userId == user.id) {
                callback()
            }
        }
    }

    override fun plus(update: PNSetMembershipEvent): Membership {
        return MembershipImpl(
            chat,
            channel,
            user,
            update.custom.let { newCustom ->
                if (newCustom != null) {
                    newCustom.value
                } else {
                    custom
                }
            },
            update.updated,
            update.eTag,
            update.status.let { newStatus ->
                if (newStatus != null) {
                    newStatus.value
                } else {
                    status
                }
            },
            update.type.let { newType ->
                if (newType != null) {
                    newType.value
                } else {
                    type
                }
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeCustomWithLastReadTimetoken(custom: CustomObject?): CustomObject? {
        if (custom == null) {
            return null
        }
        val existingTimetoken = this.custom?.get(METADATA_LAST_READ_MESSAGE_TIMETOKEN) ?: return custom
        val customMap = custom as Map<String, Any?>
        if (customMap.containsKey(METADATA_LAST_READ_MESSAGE_TIMETOKEN)) {
            return custom
        }
        return createCustomObject(
            buildMap {
                putAll(customMap)
                put(METADATA_LAST_READ_MESSAGE_TIMETOKEN, existingTimetoken)
            }
        )
    }

    private fun exists(): PNFuture<Boolean> =
        chat.metadataRepository.membershipExists(userId = user.id, channelId = channel.id)

    private fun filterThisChannel() = "channel.id == '${this.channel.id}'"

    companion object {
        private val log = Logger.withTag("MembershipImpl")

        fun streamUpdatesOn(
            memberships: Collection<Membership>,
            callback: (memberships: Collection<Membership>) -> Unit,
        ): AutoCloseable {
            if (memberships.isEmpty()) {
                log.pnError(CAN_NOT_STREAM_MEMBERSHIP_UPDATES_ON_EMPTY_LIST)
            }
            var latestMemberships = memberships
            val chat = memberships.first().chat as ChatInternal
            return chat.metadataRepository.streamMembershipUpdates(chat, memberships) { event ->
                val (newMembership, eventChannelId, eventUserId) = when (event) {
                    is MetadataEvent.MembershipUpdated ->
                        Triple(event.membership, event.membership.channel.id, event.membership.user.id)
                    is MetadataEvent.MembershipDeleted -> Triple(null, event.channelId, event.userId)
                    else -> return@streamMembershipUpdates
                }
                latestMemberships = latestMemberships
                    .asSequence()
                    .filter { membership ->
                        membership.channel.id != eventChannelId || membership.user.id != eventUserId
                    }.let { sequence ->
                        if (newMembership != null) {
                            sequence + newMembership
                        } else {
                            sequence
                        }
                    }.toList()
                callback(latestMemberships)
            }
        }

        internal fun fromMembershipDTO(chat: ChatInternal, channelMembership: PNChannelMembership, user: User) =
            MembershipImpl(
                chat,
                ChannelImpl.fromDTO(chat, channelMembership.channel),
                user,
                channelMembership.custom?.value,
                channelMembership.updated,
                channelMembership.eTag,
                channelMembership.status?.value,
                channelMembership.type?.value,
            )

        internal fun fromChannelMemberDTO(chat: ChatInternal, userMembership: PNMember, channel: Channel) =
            MembershipImpl(
                chat,
                channel,
                UserImpl.fromDTO(chat, userMembership.uuid),
                userMembership.custom?.value,
                userMembership.updated,
                userMembership.eTag,
                userMembership.status?.value,
                userMembership.type?.value,
            )
    }
}
