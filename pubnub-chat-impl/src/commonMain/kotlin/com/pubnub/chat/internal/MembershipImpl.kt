package com.pubnub.chat.internal

import co.touchlab.kermit.Logger
import com.pubnub.api.PubNubException
import com.pubnub.api.enums.PNStatusCategory
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.MembershipInclude
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteMembershipEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEvent
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEventMessage
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage.CAN_NOT_STREAM_MEMBERSHIP_UPDATES_ON_EMPTY_LIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.CONNECTION_ERROR
import com.pubnub.chat.internal.error.PubNubErrorMessage.NO_SUCH_MEMBERSHIP_EXISTS
import com.pubnub.chat.internal.error.PubNubErrorMessage.RECEIPT_EVENT_WAS_NOT_SENT_TO_CHANNEL
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.internal.utils.AccessManager
import com.pubnub.chat.types.EntityChange
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.createStatusListener
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

    override fun update(custom: CustomObject?): PNFuture<Membership> {
        return exists().thenAsync { exists ->
            if (!exists) {
                log.pnError(NO_SUCH_MEMBERSHIP_EXISTS)
            }
            chat.pubNub.setMemberships(
                userId = user.id,
                channels = listOf(PNChannelMembership.Partial(channel.id, custom)),
                include = MembershipInclude(
                    includeCustom = true,
                    includeStatus = true,
                    includeType = true,
                    includeTotalCount = true,
                    includeChannel = true,
                    includeChannelCustom = true,
                    includeChannelType = true,
                    includeChannelStatus = true
                ),
                filter = filterThisChannel()
            ).then { pnChannelMembershipArrayResult ->
                fromMembershipDTO(chat, pnChannelMembershipArrayResult.data.first(), user)
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
        return update(createCustomObject(newCustom)).alsoAsync {
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
        return streamUpdatesOn(listOf(this)) { memberships: Collection<Membership> ->
            callback(memberships.firstOrNull())
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

    private fun exists(): PNFuture<Boolean> =
        chat.pubNub.getMemberships(userId = user.id, filter = filterThisChannel()).then {
            it.data.isNotEmpty()
        }

    private fun filterThisChannel() = "channel.id == '${this.channel.id}'"

    companion object {
        private val log = Logger.withTag("MembershipImpl")

        fun streamUpdatesOn(
            memberships: Collection<Membership>,
            callback: (memberships: Collection<Membership>) -> Unit,
        ): AutoCloseable {
            return streamUpdatesOnInternal(memberships) { result ->
                result.onSuccess { (_, _, latestMemberships) ->
                    callback(latestMemberships)
                }
            }
        }

        fun streamChangesOn(
            memberships: Collection<Membership>,
            callback: (result: Result<EntityChange<Membership>>) -> Unit,
        ): AutoCloseable {
            return streamUpdatesOnInternal(memberships) { result ->
                result.onSuccess { (updatedMembership, deletedMembershipId, _) ->
                    val entityChange = updatedMembership?.let { EntityChange.Updated(it) }
                        ?: EntityChange.Removed(deletedMembershipId)
                    callback(Result.success(entityChange))
                }.onFailure { exception ->
                    callback(Result.failure(exception))
                }
            }
        }

        private fun streamUpdatesOnInternal(
            memberships: Collection<Membership>,
            callback: (result: Result<Triple<Membership?, String, Collection<Membership>>>) -> Unit
        ): AutoCloseable {
            if (memberships.isEmpty()) {
                log.pnError(CAN_NOT_STREAM_MEMBERSHIP_UPDATES_ON_EMPTY_LIST)
            }
            var latestMemberships = memberships
            val chat = memberships.first().chat as ChatInternal
            val listener = createEventListener(chat.pubNub, onObjects = { _, event ->
                val eventUuid = when (val message = event.extractedMessage) {
                    is PNDeleteMembershipEventMessage -> message.data.uuid
                    is PNSetMembershipEventMessage -> message.data.uuid
                    else -> return@createEventListener
                }
                val membership = memberships.find { it.channel.id == event.channel && it.user.id == eventUuid }
                    ?: return@createEventListener

                when (val message = event.extractedMessage) {
                    is PNSetMembershipEventMessage -> {
                        val previousMembership = latestMemberships.find { it.channel.id == event.channel && it.user.id == eventUuid }
                        val newMembership = previousMembership?.let { it + message.data }
                            ?: MembershipImpl(
                                chat,
                                channel = membership.channel,
                                user = membership.user,
                                custom = message.data.custom?.value,
                                updated = message.data.updated,
                                eTag = message.data.eTag,
                                status = message.data.status?.value,
                                type = message.data.type?.value,
                            )

                        latestMemberships = latestMemberships
                            .asSequence()
                            .filter { membership ->
                                membership.channel.id != event.channel || membership.user.id != eventUuid
                            }
                            .plus(newMembership)
                            .toList()

                        callback(Result.success(Triple(newMembership, "", latestMemberships)))
                    }
                    is PNDeleteMembershipEventMessage -> {
                        latestMemberships = latestMemberships
                            .filter { membership ->
                                membership.channel.id != event.channel || membership.user.id != eventUuid
                            }

                        // Use composite key format for membership identification
                        callback(Result.success(Triple(null, "${event.channel}:$eventUuid", latestMemberships)))
                    }
                    else -> return@createEventListener
                }
            })

            val statusListener = createStatusListener(chat.pubNub) { _, status ->
                if (status.category == PNStatusCategory.PNUnexpectedDisconnectCategory || status.category == PNStatusCategory.PNConnectionError) {
                    callback(Result.failure(status.exception ?: PubNubException(CONNECTION_ERROR)))
                }
            }
            chat.pubNub.addListener(statusListener)

            val subscriptionSet = chat.pubNub.subscriptionSetOf(memberships.map { it.channel.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()

            return AutoCloseable {
                subscriptionSet.close()
                chat.pubNub.removeListener(statusListener)
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
