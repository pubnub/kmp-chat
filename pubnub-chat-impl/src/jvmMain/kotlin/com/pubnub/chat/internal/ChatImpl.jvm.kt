package com.pubnub.chat.internal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
actual fun generateRandomUuid(): String = Uuid.random().toString()
