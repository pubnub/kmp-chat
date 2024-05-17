@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
interface Endpoint<T> {
    fun async(callback: (T)-> Unit)
}

expect class PubNub(configuration: PNConfiguration) {
    fun publish(channel: String,
                message: Any,
                meta: Any? = null,
                shouldStore: Boolean? = true,
                usePost: Boolean = false,
                replicate: Boolean = true,
                ttl: Int? = null,
    ): Endpoint<PNPublishResult>
}

expect class PNPublishResult {
    val timetoken: Long
}