package com.pubnub.chat

import com.pubnub.chat.types.HistoryResponse
import com.pubnub.kmp.PNFuture

// todo add unit and integTests
interface ThreadChannel : Channel {
    val parentMessage: Message
    val parentChannelId: String

    override fun pinMessage(message: Message): PNFuture<ThreadChannel>

    override fun unpinMessage(): PNFuture<ThreadChannel>

    override fun getHistory(startTimetoken: Long?, endTimetoken: Long?, count: Int): PNFuture<HistoryResponse<ThreadMessage>>

    fun pinMessageToParentChannel(message: ThreadMessage): PNFuture<Channel>

    fun unpinMessageFromParentChannel(): PNFuture<Channel>

    companion object
}
