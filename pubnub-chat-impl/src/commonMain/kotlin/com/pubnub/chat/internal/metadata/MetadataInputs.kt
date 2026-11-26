package com.pubnub.chat.internal.metadata

import com.pubnub.kmp.CustomObject

/** A membership to be set for a user on [channelId]. */
internal data class MembershipInput(
    val channelId: String,
    val custom: CustomObject? = null,
    val status: String? = null,
    val type: String? = null,
)

/** A channel member to be set on a channel. */
internal data class MemberInput(
    val userId: String,
    val status: String? = null,
)
