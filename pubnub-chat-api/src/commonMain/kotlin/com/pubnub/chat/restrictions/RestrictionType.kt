package com.pubnub.chat.restrictions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enum class representing the type of restriction applied to a user.
 */
@Serializable
enum class RestrictionType {
    /**
     * Represents a ban restriction, serialized as "banned".
     */
    @SerialName("banned")
    BAN,

    /**
     * Represents a mute restriction, serialized as "muted".
     */
    @SerialName("muted")
    MUTE,

    /**
     * Represents the lifting of any restriction, serialized as "lifted".
     */
    @SerialName("lifted")
    LIFT
}
