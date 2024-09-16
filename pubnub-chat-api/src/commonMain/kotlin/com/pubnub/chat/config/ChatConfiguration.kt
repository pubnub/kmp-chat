package com.pubnub.chat.config

import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.chat.types.ChannelType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

interface ChatConfiguration {
    val logLevel: LogLevel
    val typingTimeout: Duration
    val storeUserActivityInterval: Duration  // todo do we have test for this?
    val storeUserActivityTimestamps: Boolean // todo do we have test for this?
    val pushNotifications: PushNotificationsConfig
    val rateLimitFactor: Int
    val rateLimitPerChannel: Map<ChannelType, Duration>
    val customPayloads: CustomPayloads?
}

fun ChatConfiguration(
    logLevel: LogLevel = LogLevel.OFF,
    typingTimeout: Duration = 5.seconds,
    storeUserActivityInterval: Duration = 600.seconds,
    storeUserActivityTimestamps: Boolean = false,
    pushNotifications: PushNotificationsConfig = PushNotificationsConfig(
        sendPushes = false,
        deviceToken = null,
        deviceGateway = PNPushType.FCM,
        apnsTopic = null,
        apnsEnvironment = PNPushEnvironment.DEVELOPMENT
    ),
    rateLimitFactor: Int = 2,
    rateLimitPerChannel: Map<ChannelType, Duration> = RateLimitPerChannel(),
    customPayloads: CustomPayloads? = null,
): ChatConfiguration = object : ChatConfiguration {
    override val logLevel: LogLevel = logLevel
    override val typingTimeout: Duration = typingTimeout
    override val storeUserActivityInterval: Duration = maxOf(storeUserActivityInterval, 60.seconds)
    override val storeUserActivityTimestamps: Boolean = storeUserActivityTimestamps
    override val pushNotifications: PushNotificationsConfig = pushNotifications
    override val rateLimitFactor: Int = rateLimitFactor
    override val rateLimitPerChannel: Map<ChannelType, Duration> = rateLimitPerChannel
    override val customPayloads: CustomPayloads? = customPayloads
}

typealias RateLimitPerChannel = Map<ChannelType, Duration>

fun RateLimitPerChannel(
    direct: Duration = ZERO,
    group: Duration = ZERO,
    public: Duration = ZERO,
    unknown: Duration = ZERO
): RateLimitPerChannel =
    mapOf(
        ChannelType.DIRECT to direct,
        ChannelType.GROUP to group,
        ChannelType.PUBLIC to public,
        ChannelType.UNKNOWN to unknown,
    )
