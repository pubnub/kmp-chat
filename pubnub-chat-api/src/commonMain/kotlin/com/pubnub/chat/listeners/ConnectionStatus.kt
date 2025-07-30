package com.pubnub.chat.listeners

import com.pubnub.api.PubNubException

class ConnectionStatus(
    val category: ConnectionStatusCategory,
    val exception: PubNubException? = null
)
