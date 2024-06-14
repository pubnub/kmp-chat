package com.pubnub.kmp.listener

import com.pubnub.kmp.Event
import com.pubnub.kmp.MessageEvent
import com.pubnub.kmp.SignalEvent

interface Listener {// todo replace usage with kotlin#EventListener
//    fun status(statusEvent: StatusEvent)
    fun message(messageEvent: MessageEvent) {}
    fun signal(signalEvent: SignalEvent) {}
}