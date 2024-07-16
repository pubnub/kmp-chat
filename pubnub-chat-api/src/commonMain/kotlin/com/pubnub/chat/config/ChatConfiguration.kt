package com.pubnub.chat.config

import com.pubnub.chat.types.ChannelType
import kotlin.time.Duration

interface ChatConfiguration {
    val saveDebugLog: Boolean
    val typingTimeout: Duration
    val storeUserActivityInterval: Duration
    val storeUserActivityTimestamps: Boolean
    val pushNotifications: PushNotificationsConfig
    val rateLimitFactor: Int
    val rateLimitPerChannel: Map<ChannelType, Int>
    val errorLogger: Any?
    val customPayloads: CustomPayloads?
}
