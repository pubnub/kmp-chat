package com.pubnub.chat.internal.config

import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.CustomPayloads
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.types.ChannelType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun ChatConfiguration(
    saveDebugLog: Boolean = false,
    typingTimeout: Duration = 5.seconds,
    storeUserActivityInterval: Duration = 60.seconds,
    storeUserActivityTimestamps: Boolean = false,
    pushNotifications: PushNotificationsConfig = PushNotificationsConfig(false, null, PNPushType.FCM, null, PNPushEnvironment.DEVELOPMENT),
    rateLimitFactor: Int = 2,
    rateLimitPerChannel: Map<ChannelType, Int> = RateLimitPerChannel(0, 0, 0, 0),
    errorLogger: Any? = null,
    customPayloads: CustomPayloads? = null,
): ChatConfiguration = object : ChatConfiguration {
    override val saveDebugLog: Boolean = saveDebugLog
    override val typingTimeout: Duration = typingTimeout
    override val storeUserActivityInterval: Duration = storeUserActivityInterval
    override val storeUserActivityTimestamps: Boolean = storeUserActivityTimestamps
    override val pushNotifications: PushNotificationsConfig = pushNotifications
    override val rateLimitFactor: Int = rateLimitFactor
    override val rateLimitPerChannel: Map<ChannelType, Int> = rateLimitPerChannel
    override val errorLogger: Any? = errorLogger
    override val customPayloads: CustomPayloads? = customPayloads
}

fun RateLimitPerChannel(direct: Int = 0, group: Int = 0, public: Int = 0, unknown: Int = 0): Map<ChannelType, Int> =
    mapOf(
        ChannelType.DIRECT to direct,
        ChannelType.GROUP to group,
        ChannelType.PUBLIC to public,
        ChannelType.UNKNOWN to unknown,
    )
