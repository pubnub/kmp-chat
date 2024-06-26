package com.pubnub.kmp.restrictions

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.kmp.INTERNAL_MODERATION_PREFIX
import com.pubnub.kmp.error.PubNubErrorMessage.CHANNEL_ID_SHOULD_BE_DEFINED
import com.pubnub.kmp.error.PubNubErrorMessage.USER_ID_SHOULD_BE_DEFINED

// todo do we need data class here? Maybe convert to class to save some space?
data class Restriction(
    val userId: String,
    val channelId: String,
    val ban: Boolean = false,
    val mute: Boolean = false,
    val reason: String? = null
) {
    companion object {
        fun fromChannelMembershipDTO(userId: String, pnChannelMembership: PNChannelMembership): Restriction {
            val channelId =
                pnChannelMembership.channel?.id?.substringAfter(INTERNAL_MODERATION_PREFIX) ?: throw PubNubException(CHANNEL_ID_SHOULD_BE_DEFINED.message)
            val customData: Map<String, Any?>? = pnChannelMembership.custom
            val ban: Boolean = (customData?.get("ban") as? Boolean) ?: false
            val mute: Boolean = (customData?.get("mute") as? Boolean) ?: false
            val reason: String? = customData?.get("reason").toString()

            return Restriction(
                userId = userId,
                channelId = channelId,
                ban = ban,
                mute = mute,
                reason = reason
            )
        }

        fun fromMemberDTO(channelId: String, pnMember: PNMember) : Restriction{
            val userId = pnMember.uuid?.id  ?: throw PubNubException(USER_ID_SHOULD_BE_DEFINED.message)
            val customData: Map<String, Any?>? = pnMember.custom
            val ban: Boolean = (customData?.get("ban") as? Boolean) ?: false
            val mute: Boolean = (customData?.get("mute") as? Boolean) ?: false
            val reason: String? = customData?.get("reason").toString()
            return Restriction(
                userId = userId,
                channelId = channelId,
                ban = ban,
                mute = mute,
                reason = reason
            )
        }
    }
}