package com.pubnub.chat.config

import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.chat.types.ChannelType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

interface ChatConfiguration {
    val saveDebugLog: Boolean
    val typingTimeout: Duration
    val storeUserActivityInterval: Duration
    val storeUserActivityTimestamps: Boolean
    val pushNotifications: PushNotificationsConfig
    val rateLimitFactor: Int // todo use this in code
    val rateLimitPerChannel: Map<ChannelType, Duration> // todo use this in code
    val errorLogger: Any? // todo use this in code
    val customPayloads: CustomPayloads?
}

fun ChatConfiguration(
    saveDebugLog: Boolean = false,
    typingTimeout: Duration = 5.seconds,
    storeUserActivityInterval: Duration = 60.seconds,
    storeUserActivityTimestamps: Boolean = false,
    pushNotifications: PushNotificationsConfig = PushNotificationsConfig(false, null, PNPushType.FCM, null, PNPushEnvironment.DEVELOPMENT),
    rateLimitFactor: Int = 2,
    rateLimitPerChannel: Map<ChannelType, Duration> = RateLimitPerChannel(),
    errorLogger: Any? = null,
    customPayloads: CustomPayloads? = null,
): ChatConfiguration = object : ChatConfiguration {
    override val saveDebugLog: Boolean = saveDebugLog
    override val typingTimeout: Duration = typingTimeout
    override val storeUserActivityInterval: Duration = storeUserActivityInterval
    override val storeUserActivityTimestamps: Boolean = storeUserActivityTimestamps
    override val pushNotifications: PushNotificationsConfig = pushNotifications
    override val rateLimitFactor: Int = rateLimitFactor
    override val rateLimitPerChannel: Map<ChannelType, Duration> = rateLimitPerChannel
    override val errorLogger: Any? = errorLogger
    override val customPayloads: CustomPayloads? = customPayloads
}

typealias RateLimitPerChannel = Map<ChannelType, Duration>

fun RateLimitPerChannel(direct: Duration = ZERO, group: Duration = ZERO, public: Duration = ZERO, unknown: Duration = ZERO): RateLimitPerChannel =
    mapOf(
        ChannelType.DIRECT to direct,
        ChannelType.GROUP to group,
        ChannelType.PUBLIC to public,
        ChannelType.UNKNOWN to unknown,
    )
