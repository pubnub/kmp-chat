package com.pubnub.kmp

interface ThreadMessage : Message {
    val parentChannelId: String
    fun pinToParentChannel()
    fun unpinFromParentChannel()
}
