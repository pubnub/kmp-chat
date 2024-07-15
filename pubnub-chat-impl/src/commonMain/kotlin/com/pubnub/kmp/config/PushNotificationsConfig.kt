package com.pubnub.kmp.config

import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType

class PushNotificationsConfig(
    val sendPushes: Boolean,
    val deviceToken: String?,
    val deviceGateway: PNPushType,
    val apnsTopic: String?,
    val apnsEnvironment: PNPushEnvironment
)
