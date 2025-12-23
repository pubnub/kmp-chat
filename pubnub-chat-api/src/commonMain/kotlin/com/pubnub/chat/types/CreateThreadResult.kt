package com.pubnub.chat.types

import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel

/**
 * Represents the result of creating a thread on a message.
 *
 * @property threadChannel The [ThreadChannel] representing the newly created thread.
 * @property parentMessage The updated [Message] with [Message.hasThread] set to true.
 */
class CreateThreadResult(
    val threadChannel: ThreadChannel,
    val parentMessage: Message
)
