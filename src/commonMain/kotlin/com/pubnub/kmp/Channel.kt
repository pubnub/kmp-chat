package com.pubnub.kmp

import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.membership.Membership

data class Channel(
    private val chat: Chat,
    val id: String,
    val name: String? = null,
    val custom: CustomObject? = null,
    val description: String? = null,
    val updated: String? = null,
    val status: String? = null,
    val type: ChannelType? = null
){
    private val suggestedNames = mutableMapOf<String, List<Membership>>()
    private var disconnect: (() -> Unit)? = null
    private var typingSent = false
    private var typingSentTimer: String? = null //todo type should be something like TimerTask instead of String maybe use expect/actual ?
    private var typingIndicators = mutableMapOf<String, String>() //todo probably should be something like mutableMapOf<String, TimerTask>()
    private val sendTextRateLimiter: String? = null // todo should be ExponentialRateLimiter instead of String

    fun update(
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        updated: String? = null,
        status: String? = null,
        type: ChannelType? = null,
        callback: (Result<Channel>) -> Unit
    ) {
        return chat.updateChannel(id, name, custom, description, updated, status, type, callback)
    }

    fun delete(soft: Boolean = false, callback: (Result<Channel>) -> Unit ){
        return chat.deleteChannel(id, soft, callback)
    }

    fun forwardMessage(message: Message, callback: (Result<Unit>) -> Unit){
        return chat.forwardMessage(message, this.id, callback)
    }
}


enum class ChannelType{
    DIRECT, GROUP, PUBLIC;
}