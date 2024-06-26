package com.pubnub.kmp.membership

import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.kmp.Channel
import com.pubnub.kmp.ChatImpl
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.Message
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.User
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync

data class Membership(
    private val chat: ChatImpl,
    val channel: Channel,
    val user: User,
    val custom: Map<String,Any?>?
) {
    val lastReadMessageTimetoken: Long?
        get() = custom?.get("lastReadMessageTimetoken") as? Long

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
            ).then {
                fromMembershipDTO(chat, it.data.first(), user)
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
            ).then { it.channels[channel.id]!! }
        } ?: (null as Long?).asFuture()
    }

    private fun exists(): PNFuture<Boolean> =
        chat.pubNub.getMemberships(uuid = user.id, filter = filterThisChannel()).then {
            it.data.isNotEmpty()
        }

    private fun filterThisChannel() = "channel.id == '${this.channel.id}'"

    companion object {
        internal fun fromMembershipDTO(chat: ChatImpl, channelMembership: PNChannelMembership, user: User) = Membership(
            chat,
            ChannelImpl.fromDTO(chat, channelMembership.channel!!),
            user,
            channelMembership.custom
        )

        internal fun fromChannelMemberDTO(chat: ChatImpl, userMembership: PNMember, channel: Channel) = Membership(
            chat,
            channel,
            User.fromDTO(chat, userMembership.uuid!!),
            userMembership.custom
        )
    }
}