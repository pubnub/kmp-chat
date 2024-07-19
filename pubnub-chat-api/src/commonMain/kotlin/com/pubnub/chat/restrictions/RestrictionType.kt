package com.pubnub.chat.restrictions

import com.pubnub.api.PubNubException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val RESTRICTION_TYPE_BANNED = "banned"
private const val RESTRICTION_TYPE_MUTED = "muted"
private const val RESTRICTION_TYPE_LIFTED = "lifted"

@Serializable
enum class RestrictionType(val stringValue: String) {
    @SerialName(RESTRICTION_TYPE_BANNED)
    BAN(RESTRICTION_TYPE_BANNED),

    @SerialName(RESTRICTION_TYPE_MUTED)
    MUTE(RESTRICTION_TYPE_MUTED),

    @SerialName(RESTRICTION_TYPE_LIFTED)
    LIFT(RESTRICTION_TYPE_LIFTED);

    companion object {
        fun from(type: String): RestrictionType {
            return entries.find { it -> it.stringValue == type }
                ?: throw throw PubNubException("unknown RestrictionType: $type")
        }
    }
}
