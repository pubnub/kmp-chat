package com.pubnub.chat.internal.restrictions

import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.error.PubNubErrorMessage.CHANNEL_ID_MUST_BE_DEFINED
import com.pubnub.chat.internal.error.PubNubErrorMessage.NO_SUCH_MEMBERSHIP_EXISTS
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.restrictions.Restriction
import org.lighthousegames.logging.logging

class RestrictionImpl {
    companion object {
        private val log = logging()

        fun fromChannelMembershipDTO(userId: String, pnChannelMembership: PNChannelMembership): Restriction {
            val channelId =
                pnChannelMembership.channel?.id?.substringAfter(INTERNAL_MODERATION_PREFIX) ?: log.pnError(
                    CHANNEL_ID_MUST_BE_DEFINED
                )
            val customData: Map<String, Any?>? = pnChannelMembership.custom
            val ban: Boolean = (customData?.get("ban") as? Boolean) ?: false
            val mute: Boolean = (customData?.get("mute") as? Boolean) ?: false
            val reason: String? = customData?.get("reason")?.toString()

            return Restriction(
                userId = userId,
                channelId = channelId,
                ban = ban,
                mute = mute,
                reason = reason
            )
        }

        fun fromMemberDTO(channelId: String, pnMember: PNMember): Restriction {
            val userId = pnMember.uuid?.id ?: log.pnError(NO_SUCH_MEMBERSHIP_EXISTS)
            val customData: Map<String, Any?>? = pnMember.custom
            val ban: Boolean = (customData?.get("ban") as? Boolean) ?: false
            val mute: Boolean = (customData?.get("mute") as? Boolean) ?: false
            val reason: String? = customData?.get("reason")?.toString()
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
