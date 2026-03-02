package com.pubnub.chat.internal.user

import com.pubnub.chat.restrictions.RestrictionType
import com.pubnub.chat.types.ChannelType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer

/**
 * Internal payload classes for serializing and deserializing user-targeted events.
 */

@Serializable
@SerialName("mention")
internal data class MentionPayload(
    @Serializable(with = LongAsStringSerializer::class) val messageTimetoken: Long,
    val channel: String,
    val parentChannel: String? = null,
)

@Serializable
@SerialName("invite")
internal data class InvitePayload(
    val channelType: ChannelType,
    val channelId: String,
)

@Serializable
@SerialName("moderation")
internal data class ModerationPayload(
    val channelId: String,
    val restriction: RestrictionType,
    val reason: String? = null,
)
