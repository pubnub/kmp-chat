package com.pubnub.kmp.message_draft

import com.pubnub.kmp.Message
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannels
import com.pubnub.kmp.types.TextLink

class MessageDraftPreview(
    val text: String,
    val mentionedUsers: MessageMentionedUsers,
    val mentionedChannels: MessageReferencedChannels,
    val textLinks: List<TextLink>,
    val quotedMessage: Message?
)