package com.pubnub.chat.internal.restrictions

import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.RESTRICTION_BAN
import com.pubnub.chat.internal.RESTRICTION_MUTE
import com.pubnub.chat.internal.RESTRICTION_REASON
import com.pubnub.chat.restrictions.Restriction

class RestrictionImpl {
    companion object {
        fun fromChannelMembershipDTO(userId: String, pnChannelMembership: PNChannelMembership): Restriction {
            val channelId =
                pnChannelMembership.channel.id.substringAfter(INTERNAL_MODERATION_PREFIX)
            val customData: Map<String, Any?>? = pnChannelMembership.custom?.value
            val ban: Boolean = (customData?.get(RESTRICTION_BAN) as? Boolean) == true
            val mute: Boolean = (customData?.get(RESTRICTION_MUTE) as? Boolean) == true
            val reason: String? = customData?.get(RESTRICTION_REASON)?.toString()

            return Restriction(
                userId = userId,
                channelId = channelId,
                ban = ban,
                mute = mute,
                reason = reason
            )
        }

        fun fromMemberDTO(channelId: String, pnMember: PNMember): Restriction {
            val userId = pnMember.uuid.id
            val customData: Map<String, Any?>? = pnMember.custom?.value
            val ban: Boolean = (customData?.get(RESTRICTION_BAN) as? Boolean) == true
            val mute: Boolean = (customData?.get(RESTRICTION_MUTE) as? Boolean) == true
            val reason: String? = customData?.get(RESTRICTION_REASON)?.toString()
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
