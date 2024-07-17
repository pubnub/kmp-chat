package com.pubnub.chat.internal

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteMembershipEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEventMessage
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage
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
import tryLong

data class MembershipImpl(
    override val chat: ChatInternal,
    override val channel: Channel,
    override val user: User,
    override val custom: Map<String, Any?>?,
    override val updated: String?,
    override val eTag: String?,
) : Membership {
    override val lastReadMessageTimetoken: Long? // todo shouldn't we call here getMetadata.custom.lastReadMessageTimetoken to have current data?
        get() {
            return custom?.get("lastReadMessageTimetoken").tryLong()
        }

    override fun setLastReadMessage(message: Message): PNFuture<Membership> {
        return setLastReadMessageTimetoken(message.timetoken)
    }

    override fun update(custom: CustomObject): PNFuture<Membership> {
        return exists().thenAsync { exists ->
            if (!exists) {
                throw PubNubException(PubNubErrorMessage.NO_SUCH_MEMBERSHIP_EXISTS)
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
            put("lastReadMessageTimetoken", timetoken)
        }
        return update(createCustomObject(newCustom)).alsoAsync {
            val canISendSignal = AccessManager(chat).canI(AccessManager.Permission.WRITE, AccessManager.ResourceType.CHANNELS, channel.id)
            if (canISendSignal) {
                chat.emitEvent(channel.id, EventContent.Receipt(timetoken))
            } else {
                if (chat.config.saveDebugLog) {
                    println(
                        "'receipt' event was not sent to channel '${this.channel.id}' because PAM did not allow it."
                    ) // todo change to logging
                }
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

    override fun streamUpdates(callback: (membership: Membership) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) {
            callback(it.first())
        }
    }

    private fun exists(): PNFuture<Boolean> =
        chat.pubNub.getMemberships(uuid = user.id, filter = filterThisChannel()).then {
            it.data.isNotEmpty()
        }

    private fun filterThisChannel() = "channel.id == '${this.channel.id}'"

    companion object {
        fun streamUpdatesOn(
            memberships: Collection<Membership>,
            callback: (memberships: Collection<Membership>) -> Unit,
        ): AutoCloseable {
            if (memberships.isEmpty()) {
                throw PubNubException("Cannot stream membership updates on an empty list")
            }
            val chat = memberships.first().chat as ChatInternal
            val listener = createEventListener(chat.pubNub, onObjects = { pubNub, event ->
                val eventUuid = when (val message = event.extractedMessage) {
                    is PNDeleteMembershipEventMessage -> message.data.uuid
                    is PNSetMembershipEventMessage -> message.data.uuid
                    else -> return@createEventListener
                }
                val membership = memberships.find { it.channel.id == event.channel && it.user.id == eventUuid }
                    ?: return@createEventListener
                val newMembership = when (val message = event.extractedMessage) {
                    is PNSetMembershipEventMessage -> MembershipImpl(
                        chat,
                        user = membership.user,
                        channel = membership.channel,
                        custom = message.data.custom,
                        updated = message.data.updated,
                        eTag = message.data.eTag
                    )

                    is PNDeleteMembershipEventMessage -> MembershipImpl(
                        chat,
                        user = membership.user,
                        channel = membership.channel,
                        custom = null,
                        updated = null,
                        eTag = null
                    ) // todo verify behavior with TS Chat SDK
                    else -> return@createEventListener
                }
                val newMemberships = memberships.map {
                    if (it.channel.id == newMembership.channel.id && it.user.id == newMembership.user.id) {
                        newMembership
                    } else {
                        it
                    }
                }
                callback(newMemberships)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(memberships.map { it.channel.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        internal fun fromMembershipDTO(chat: ChatInternal, channelMembership: PNChannelMembership, user: User) = MembershipImpl(
            chat,
            ChannelImpl.fromDTO(chat, channelMembership.channel!!),
            user,
            channelMembership.custom,
            channelMembership.updated,
            channelMembership.eTag
        )

        internal fun fromChannelMemberDTO(chat: ChatInternal, userMembership: PNMember, channel: Channel) = MembershipImpl(
            chat,
            channel,
            UserImpl.fromDTO(chat, userMembership.uuid!!),
            userMembership.custom,
            userMembership.updated,
            userMembership.eTag,
        )
    }
}
