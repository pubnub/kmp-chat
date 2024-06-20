package com.pubnub.kmp.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RestrictionType(val stringValue: String) {
    @SerialName("banned")
    BAN("banned"),

    @SerialName("muted")
    MUTE("muted"),

    @SerialName("lifted")
    LIFT("lifted");

    //todo check if this is required. If not required remove stringValue
    companion object{
        fun from(text: String): RestrictionType?{
            return values().find {it.stringValue== text }
        }
    }

    override fun toString(): String {
        return stringValue
    }
}