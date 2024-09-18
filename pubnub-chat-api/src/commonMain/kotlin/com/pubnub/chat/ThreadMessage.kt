package com.pubnub.chat

import com.pubnub.kmp.PNFuture

interface ThreadMessage : BaseMessage<ThreadMessage> {
    val parentChannelId: String

    fun pinToParentChannel(): PNFuture<Channel>

    fun unpinFromParentChannel(): PNFuture<Channel>

    companion object
}
