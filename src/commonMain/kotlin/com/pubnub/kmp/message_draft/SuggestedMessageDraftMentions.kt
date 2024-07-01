package com.pubnub.kmp.message_draft

import com.pubnub.kmp.Channel
import com.pubnub.kmp.User
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel

class SuggestedMessageDraftMentions private constructor(
    val users: Map<Int, List<MessageMentionedUser>>,
    val channels: Map<Int, List<MessageReferencedChannel>>
) {
    companion object {
        fun create(
            userMentionedAt: Int?,
            users: List<User>,
            channelMentionedAt: Int?,
            channels: List<Channel>
        ): SuggestedMessageDraftMentions {
            return SuggestedMessageDraftMentions(
                users = userMentionedAt?.let {
                    mapOf(it to users.map { user -> MessageMentionedUser(user.id, user.name.orEmpty()) })
                } ?: emptyMap(),
                channels = channelMentionedAt?.let {
                    mapOf(it to channels.map { channel -> MessageReferencedChannel(channel.id, channel.name.orEmpty()) })
                } ?: emptyMap()
            )
        }
    }
}