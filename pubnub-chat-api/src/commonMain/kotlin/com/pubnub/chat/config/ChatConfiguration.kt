package com.pubnub.chat.config

import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.chat.types.ChannelType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

interface ChatConfiguration {
    /**
     * Specifies if any Chat SDK-related errors should be logged. It's disabled by default.
     * Available options include: OFF, ERROR, WARN, INFO, DEBUG, and VERBOSE.
     */
    val logLevel: LogLevel

    /**
     * Specifies the default timeout after which the typing indicator automatically stops when no typing signals are received.
     * The default value is set to 5 seconds, minimal value is 1 seconds.
     */
    val typingTimeout: Duration

    /**
     * Specifies how often the user global presence in the app should be updated. Requires storeUserActivityTimestamps
     * to be set to true. The default value is set to 60 seconds, and that's the minimum possible value.
     * If you try to set it to a lower value, you'll get the storeUserActivityInterval must be at least 60000ms error.
     */
    val storeUserActivityInterval: Duration

    /**
     * Specifies if you want to track the user's global presence in your chat app. The user's activity is tracked
     * through [com.pubnub.chat.User.lastActiveTimestamp].
     */
    val storeUserActivityTimestamps: Boolean

    /**
     * List of parameters you must set if you want to enable sending/receiving mobile push notifications for phone
     * devices, either through Apple Push Notification service (APNS) or Firebase Cloud Messaging (FCM).
     */
    val pushNotifications: PushNotificationsConfig

    /**
     * The so-called "exponential backoff" which multiplicatively decreases the rate at which messages are published on channels.
     *
     * It's bound to the [rateLimitPerChannel] parameter and is meant to prevent message spamming caused by excessive retries.
     *
     * The default value of 2 means that if you set rateLimitPerChannel for direct channels to 1 second and try to send
     * three messages on such a channel type within the span of one second, the second message will be published
     * one second after the first one (just like the rateLimitPerChannel value states), but the third one will be
     * published two seconds after the second one, meaning the publishing time is multiplied by 2.
     */
    val rateLimitFactor: Int

    /**
     * Client-side limit that states the rate at which messages can be published on a given channel type.
     * Its purpose is to prevent message spamming in your chat app.
     * This parameter takes an object with these three parameters: direct, group, and public.
     * For example, if you decide that messages on all direct channels must be published no more often than every second,
     * this is how you set it: ChatConfiguration(rateLimitPerChannel = RateLimitPerChannel(direct = 1.seconds)).
     */
    val rateLimitPerChannel: Map<ChannelType, Duration>

    /**
     * Property that lets you define your custom message payload to be sent and/or received by Chat SDK on one or all
     * channels, whenever it differs from the default message.text Chat SDK payload.
     * It also lets you configure your own message actions whenever a message is edited or deleted.
     */
    val customPayloads: CustomPayloads?

    /**
     * Enable automatic syncing of the [com.pubnub.chat.mutelist.MutedUsersManager] data with App Context,
     * using the current `userId` as the key.
     *
     * Specifically, the data is saved in the `custom` object of the following User in App Context:
     *
     * ```
     * PN_PRIV.{userId}.mute.1
     * ```
     *
     * where {userId} is the current [com.pubnub.api.v2.PNConfiguration.userId].
     *
     * If using Access Manager, the access token must be configured with the appropriate rights to subscribe to that
     * channel, and get, update, and delete the App Context User with that id.
     *
     * Due to App Context size limits, the number of muted users is limited to around 200 and will result in sync errors
     * when the limit is exceeded. The list will not sync until its size is reduced.
     */
    val syncMutedUsers: Boolean
}

fun ChatConfiguration(
    logLevel: LogLevel = LogLevel.WARN,
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
    syncMutedUsers: Boolean = false,
): ChatConfiguration = object : ChatConfiguration {
    override val logLevel: LogLevel = logLevel
    override val typingTimeout: Duration = typingTimeout
    override val storeUserActivityInterval: Duration = maxOf(storeUserActivityInterval, 60.seconds)
    override val storeUserActivityTimestamps: Boolean = storeUserActivityTimestamps
    override val pushNotifications: PushNotificationsConfig = pushNotifications
    override val rateLimitFactor: Int = rateLimitFactor
    override val rateLimitPerChannel: Map<ChannelType, Duration> = rateLimitPerChannel
    override val customPayloads: CustomPayloads? = customPayloads
    override val syncMutedUsers: Boolean = syncMutedUsers
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
