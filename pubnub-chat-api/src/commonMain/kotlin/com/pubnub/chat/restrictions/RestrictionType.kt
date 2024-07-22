package com.pubnub.chat.restrictions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RestrictionType {
    @SerialName("banned")
    BAN,

    @SerialName("muted")
    MUTE,

    @SerialName("lifted")
    LIFT
}
