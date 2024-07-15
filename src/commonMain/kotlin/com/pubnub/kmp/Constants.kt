package com.pubnub.kmp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val DELETED = "Deleted"
internal const val ID_IS_REQUIRED = "Id is required"
internal const val CHANNEL_ID_IS_REQUIRED = "Channel Id is required"
internal const val ORIGINAL_PUBLISHER = "originalPublisher"
internal const val HTTP_ERROR_404 = 404
internal const val INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_"
internal const val MESSAGE_THREAD_ID_PREFIX = "PUBNUB_INTERNAL_THREAD"
internal val MINIMAL_TYPING_INDICATOR_TIMEOUT: Duration = 1.seconds
internal const val THREAD_ROOT_ID = "threadRootId"
internal const val INTERNAL_ADMIN_CHANNEL = "PUBNUB_INTERNAL_ADMIN_CHANNEL"
