package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteMembershipEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEventMessage
import com.pubnub.kmp.channel.BaseChannel
import com.pubnub.kmp.channel.ChannelImpl
import tryLong

data class Membership(
    private val chat: Chat,
    val channel: Channel,
    val user: User,
    val custom: Map<String, Any?>?,
    val updated: String?,
    val eTag: String?,
) {
    val lastReadMessageTimetoken: Long? // todo shouldn't we call here getMetadata.custom.lastReadMessageTimetoken to have current data?
        get() {
            return custom?.get("lastReadMessageTimetoken").tryLong()
        }

    fun setLastReadMessage(message: Message): PNFuture<Membership> {
        return setLastReadMessageTimetoken(message.timetoken)
    }

    fun update(custom: CustomObject): PNFuture<Membership> {
        return exists().thenAsync { exists ->
            if (!exists) {
                error("No such membership exists")
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

    fun setLastReadMessageTimetoken(time: Long): PNFuture<Membership> {
        val newCustom = buildMap {
            custom?.let { putAll(it) }
            put("lastReadMessageTimetoken", time)
        }
        return update(createCustomObject(newCustom)).alsoAsync {
            // todo implement when this.chat.accessManager.canI is done
            Unit.asFuture()
        }
    }

    fun getUnreadMessagesCount(): PNFuture<Long?> {
        return lastReadMessageTimetoken?.let { timetoken ->
            chat.pubNub.messageCounts(
                channels = listOf(channel.id),
                channelsTimetoken = listOf(timetoken)
            ).then { pnMessageCountResult ->
                pnMessageCountResult.channels[channel.id]!!
            }
        } ?: (null as Long?).asFuture()
    }

    fun streamUpdates(callback: (membership: Membership) -> Unit): AutoCloseable {
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
            val chat = (memberships.first() as BaseChannel<*, *>).chat
            val listener = createEventListener(chat.pubNub, onObjects = { pubNub, event ->
                val eventUuid = when (val message = event.extractedMessage) {
                    is PNDeleteMembershipEventMessage -> message.data.uuid
                    is PNSetMembershipEventMessage -> message.data.uuid
                    else -> return@createEventListener
                }
                val membership = memberships.find { it.channel.id == event.channel && it.user.id == eventUuid }
                    ?: return@createEventListener
                val newMembership = when (val message = event.extractedMessage) {
                    is PNSetMembershipEventMessage -> Membership(
                        chat,
                        user = membership.user,
                        channel = membership.channel,
                        custom = message.data.custom,
                        updated = message.data.updated,
                        eTag = message.data.eTag
                    )

                    is PNDeleteMembershipEventMessage -> Membership(
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

        internal fun fromMembershipDTO(chat: Chat, channelMembership: PNChannelMembership, user: User) = Membership(
            chat,
            ChannelImpl.fromDTO(chat, channelMembership.channel!!),
            user,
            channelMembership.custom,
            channelMembership.updated,
            channelMembership.eTag
        )

        internal fun fromChannelMemberDTO(chat: Chat, userMembership: PNMember, channel: Channel) = Membership(
            chat,
            channel,
            User.fromDTO(chat, userMembership.uuid!!),
            userMembership.custom,
            userMembership.updated,
            userMembership.eTag,
        )
    }
}