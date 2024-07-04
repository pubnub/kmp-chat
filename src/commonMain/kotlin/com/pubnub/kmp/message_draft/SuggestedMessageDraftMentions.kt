package com.pubnub.kmp.message_draft

import com.pubnub.kmp.Channel
import com.pubnub.kmp.User
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel

class SuggestedMessageDraftMentions private constructor(
    val users: Map<Int, List<User>>,
    val channels: Map<Int, List<Channel>>
) {
    companion object {
        fun create(
            userMentionedAt: Int?,
            users: List<User>,
            channelMentionedAt: Int?,
            channels: List<Channel>
        ): SuggestedMessageDraftMentions {
            return SuggestedMessageDraftMentions(
                users = userMentionedAt?.let { mapOf(it to users) } ?: emptyMap(),
                channels = channelMentionedAt?.let { mapOf(it to channels) } ?: emptyMap()
            )
        }
    }
}