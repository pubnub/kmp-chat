package com.pubnub.chat

import com.pubnub.kmp.PNFuture

interface ThreadMessage : Message {
    val parentChannelId: String

    fun pinToParentChannel(): PNFuture<Channel>

    fun unpinFromParentChannel(): PNFuture<Channel>

    companion object
}
