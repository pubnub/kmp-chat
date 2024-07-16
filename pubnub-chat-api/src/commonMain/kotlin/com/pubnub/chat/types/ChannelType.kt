package com.pubnub.chat.types

import kotlinx.serialization.SerialName

private const val CHANNELTYPE_DIRECT = "direct"
private const val CHANNELTYPE_GROUP = "group"
private const val CHANNELTYPE_PUBLIC = "public"
private const val CHANNELTYPE_UNKKNOWN = "unknown"

enum class ChannelType(val stringValue: String) {
    @SerialName(CHANNELTYPE_DIRECT)
    DIRECT(CHANNELTYPE_DIRECT),

    @SerialName(CHANNELTYPE_GROUP)
    GROUP(CHANNELTYPE_GROUP),

    @SerialName(CHANNELTYPE_PUBLIC)
    PUBLIC(CHANNELTYPE_PUBLIC),

    @SerialName(CHANNELTYPE_UNKKNOWN)
    UNKNOWN(CHANNELTYPE_UNKKNOWN);

    companion object {
        fun from(type: String?): ChannelType {
            return entries.find { it.stringValue == type } ?: UNKNOWN
        }
    }
}
