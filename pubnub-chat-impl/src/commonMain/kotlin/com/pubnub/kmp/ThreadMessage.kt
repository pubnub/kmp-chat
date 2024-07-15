package com.pubnub.kmp

interface ThreadMessage : Message {
    val parentChannelId: String

    fun pinToParentChannel(): PNFuture<Channel>

    fun unpinFromParentChannel(): PNFuture<Channel>
}
