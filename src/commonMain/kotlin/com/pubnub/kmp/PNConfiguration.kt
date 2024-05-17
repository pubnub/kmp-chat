@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class PNConfiguration(
    val userId: UserId,
    val subscribeKey: String,
    val publishKey: String,
)

@JsExport
class UserId(
    val value: String
)
