package com.pubnub.kmp

import com.pubnub.kmp.membership.Membership

class Channel(
    private val chat: Chat,
    val id: String,
    val name: String? = null,
    val custom: Any? = null,
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
}


enum class ChannelType{
    DIRECT, GROUP, PUBLIC;
}