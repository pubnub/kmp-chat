package com.pubnub.chat

import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

interface Membership {
    val chat: Chat
    val channel: Channel
    val user: User
    val custom: Map<String, Any?>?
    val updated: String?
    val eTag: String?
    val lastReadMessageTimetoken: Long? // todo shouldn't we call here getMetadata.custom.lastReadMessageTimetoken to have current data?

    fun setLastReadMessage(message: Message): PNFuture<Membership>

    fun update(custom: CustomObject): PNFuture<Membership>

    fun setLastReadMessageTimetoken(timetoken: Long): PNFuture<Membership>

    fun getUnreadMessagesCount(): PNFuture<Long?>

    fun streamUpdates(callback: (membership: Membership?) -> Unit): AutoCloseable

    companion object
}
