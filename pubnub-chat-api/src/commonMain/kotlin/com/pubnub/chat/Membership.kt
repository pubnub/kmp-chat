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
    val lastReadMessageTimetoken: Long?

    // todo do we want to have test for this?
    fun setLastReadMessage(message: Message): PNFuture<Membership>

    fun update(custom: CustomObject): PNFuture<Membership>

    // todo do we have test for this?
    fun setLastReadMessageTimetoken(timetoken: Long): PNFuture<Membership>

    fun getUnreadMessagesCount(): PNFuture<Long?>

    // todo do we have test for this?
    fun streamUpdates(callback: (membership: Membership?) -> Unit): AutoCloseable

    companion object
}
