package com.pubnub.kmp

import kotlinx.datetime.Clock

interface ThreadChannel : Channel {
    val parentMessage: Message
    val clock: Clock
    val parentChannelId: String

    override fun pinMessage(message: Message): PNFuture<ThreadChannel>
    override fun unpinMessage(): PNFuture<ThreadChannel>
    override fun getHistory(startTimetoken: Long?, endTimetoken: Long?, count: Int?): PNFuture<List<ThreadMessage>>

    fun pinMessageToParentChannel(message: ThreadMessage): PNFuture<Channel>
    fun unpinMessageFromParentChannel(): PNFuture<Channel>
}
