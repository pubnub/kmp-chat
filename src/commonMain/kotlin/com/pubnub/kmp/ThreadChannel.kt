package com.pubnub.kmp

import kotlinx.datetime.Clock

interface ThreadChannel : Channel {
    val parentMessage: Message
    val clock: Clock
    val parentChannelId: String

    fun pinMessageToParentChannel(message: ThreadMessage): PNFuture<Channel>
    fun unpinMessageFromParentChannel(): PNFuture<Channel>
}
