package com.pubnub.chat.internal

import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteMembershipEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEvent
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEventMessage
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage.CAN_NOT_STREAM_MEMBERSHIP_UPDATES_ON_EMPTY_LIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.NO_SUCH_MEMBERSHIP_EXISTS
import com.pubnub.chat.internal.error.PubNubErrorMessage.RECEIPT_EVENT_WAS_NOT_SENT_TO_CHANNEL
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.internal.utils.AccessManager
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import org.lighthousegames.logging.logging
import tryLong

data class MembershipImpl(
    override val chat: ChatInternal,
    override val channel: Channel,
    override val user: User,
    override val custom: Map<String, Any?>?,
    override val updated: String?,
    override val eTag: String?,
) : Membership {
    override val lastReadMessageTimetoken: Long?
        get() {
            return custom?.get(METADATA_LAST_READ_MESSAGE_TIMETOKEN).tryLong()
        }

    override fun setLastReadMessage(message: Message): PNFuture<Membership> {
        return setLastReadMessageTimetoken(message.timetoken)
    }

    override fun update(custom: CustomObject): PNFuture<Membership> {
        return exists().thenAsync { exists ->
            if (!exists) {
                log.pnError(NO_SUCH_MEMBERSHIP_EXISTS)
            }
            chat.pubNub.setMemberships(
                uuid = user.id,
                channels = listOf(PNChannelMembership.Partial(channel.id, custom)),
                includeCustom = true,
                includeCount = true,
                includeType = true,
                includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
                filter = filterThisChannel()
            ).then { pnChannelMembershipArrayResult ->
                fromMembershipDTO(chat, pnChannelMembershipArrayResult.data.first(), user)
            }
        }
    }

    override fun setLastReadMessageTimetoken(timetoken: Long): PNFuture<Membership> {
        val newCustom = buildMap {
            custom?.let { putAll(it) }
            put(METADATA_LAST_READ_MESSAGE_TIMETOKEN, timetoken)
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
                log.warn { "$RECEIPT_EVENT_WAS_NOT_SENT_TO_CHANNEL${this.channel.id}" }
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
        return streamUpdatesOn(listOf(this)) {
            callback(it.firstOrNull())
        }
    }

    override fun plus(update: PNSetMembershipEvent): Membership {
        return MembershipImpl(
            chat,
            channel,
            user,
            update.custom?.value ?: custom,
            update.updated,
            update.eTag
        )
    }

    private fun exists(): PNFuture<Boolean> =
        chat.pubNub.getMemberships(uuid = user.id, filter = filterThisChannel()).then {
            it.data.isNotEmpty()
        }

    private fun filterThisChannel() = "channel.id == '${this.channel.id}'"

    companion object {
        private val log = logging()

        fun streamUpdatesOn(
            memberships: Collection<Membership>,
            callback: (memberships: Collection<Membership>) -> Unit,
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
                val newMembership = when (val message = event.extractedMessage) {
                    is PNSetMembershipEventMessage -> {
                        val previousMembership = latestMemberships.find { it.channel.id == event.channel && it.user.id == eventUuid }
                        previousMembership?.let { it + message.data }
                            ?: MembershipImpl(
                                chat,
                                user = membership.user,
                                channel = membership.channel,
                                custom = message.data.custom?.value,
                                updated = message.data.updated,
                                eTag = message.data.eTag
                            )
                    }
                    is PNDeleteMembershipEventMessage -> null
                    else -> return@createEventListener
                }
                latestMemberships = latestMemberships.asSequence().filter { membership ->
                    membership.channel.id != event.channel || membership.user.id != eventUuid
                }.let { sequence ->
                    if (newMembership != null) {
                        sequence + newMembership
                    } else {
                        sequence
                    }
                }.toList()
                callback(latestMemberships)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(memberships.map { it.channel.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        internal fun fromMembershipDTO(chat: ChatInternal, channelMembership: PNChannelMembership, user: User) =
            MembershipImpl(
                chat,
                ChannelImpl.fromDTO(chat, channelMembership.channel),
                user,
                channelMembership.custom?.value,
                channelMembership.updated,
                channelMembership.eTag
            )

        internal fun fromChannelMemberDTO(chat: ChatInternal, userMembership: PNMember, channel: Channel) =
            MembershipImpl(
                chat,
                channel,
                UserImpl.fromDTO(chat, userMembership.uuid),
                userMembership.custom?.value,
                userMembership.updated,
                userMembership.eTag,
            )
    }
}
