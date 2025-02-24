package com.pubnub.chat.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val CHANNELTYPE_DIRECT = "direct"
private const val CHANNELTYPE_GROUP = "group"
private const val CHANNELTYPE_PUBLIC = "public"
private const val CHANNELTYPE_UNKKNOWN = "unknown"
private const val CHANNELTYPE_PUBNUB_PRIVATE = "pn.prv"

/**
 * Enum class representing the different types of channels that can be created.
 *
 * @property stringValue The string representation of the channel type, used for serialization.
 */
@Serializable
enum class ChannelType(val stringValue: String) {
    /**
     * A direct channel, used for one-on-one communication.
     */
    @SerialName(CHANNELTYPE_DIRECT)
    DIRECT(CHANNELTYPE_DIRECT),

    /**
     * A group channel, used for communication between multiple users in a private group.
     */
    @SerialName(CHANNELTYPE_GROUP)
    GROUP(CHANNELTYPE_GROUP),

    /**
     * A public channel, used for communication that is accessible to all users.
     */
    @SerialName(CHANNELTYPE_PUBLIC)
    PUBLIC(CHANNELTYPE_PUBLIC),

    /**
     * An unknown channel type, used as a fallback when the type is unrecognized.
     */
    @SerialName(CHANNELTYPE_UNKKNOWN)
    UNKNOWN(CHANNELTYPE_UNKKNOWN),

    /**
     * A technical channel used by chat for storing additional metadata. Not for normal use.
     */
    @SerialName(CHANNELTYPE_PUBNUB_PRIVATE)
    PUBNUB_PRIVATE(CHANNELTYPE_PUBNUB_PRIVATE),

    ;

    companion object {
        fun from(type: String?): ChannelType {
            return entries.find { it.stringValue == type } ?: UNKNOWN
        }
    }
}
