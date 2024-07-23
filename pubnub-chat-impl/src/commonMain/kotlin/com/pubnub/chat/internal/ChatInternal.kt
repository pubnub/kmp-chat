package com.pubnub.chat.internal

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.Chat
import com.pubnub.chat.User
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.PNFuture

interface ChatInternal : Chat {
    fun publish(
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean = true,
        usePost: Boolean = false,
        replicate: Boolean = true,
        ttl: Int? = null,
        mergeMessageWith: Map<String, Any>? = null,
    ): PNFuture<PNPublishResult>

    fun signal(
        channelId: String,
        message: EventContent,
        mergeMessageWith: Map<String, Any>? = null
    ): PNFuture<PNPublishResult>

    fun createUser(user: User): PNFuture<User>
}
