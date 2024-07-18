package com.pubnub.chat.config

import com.pubnub.chat.types.ChannelType
import kotlin.time.Duration

interface ChatConfiguration {
    val saveDebugLog: Boolean
    val typingTimeout: Duration
    val storeUserActivityInterval: Duration
    val storeUserActivityTimestamps: Boolean
    val pushNotifications: PushNotificationsConfig
    val rateLimitFactor: Int // todo use this in code
    val rateLimitPerChannel: Map<ChannelType, Int> // todo use this in code
    val errorLogger: Any? // todo use this in code
    val customPayloads: CustomPayloads? // todo use this in code
}
