package com.pubnub.kmp.types

import kotlinx.serialization.SerialName

private const val stringDirect = "direct"
private const val stringGroup = "group"
private const val stringPublic = "public"
private const val stringUnknown = "unknown"

enum class ChannelType(val stringValue: String) {
    @SerialName(stringDirect) DIRECT(stringDirect),
    @SerialName(stringGroup) GROUP(stringGroup),
    @SerialName(stringPublic) PUBLIC(stringPublic),
    @SerialName(stringUnknown) UNKNOWN(stringUnknown);

    companion object {
        fun from(type: String?): ChannelType {
            return entries.find { it.stringValue == type } ?: UNKNOWN
        }
    }
}
