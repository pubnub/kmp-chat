@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


class PNConfiguration(
    val userId: UserId,
    val subscribeKey: String,
    val publishKey: String,
)


class UserId(
    val value: String
)
