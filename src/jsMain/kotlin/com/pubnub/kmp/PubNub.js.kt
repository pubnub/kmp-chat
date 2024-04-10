@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import createPubNub
import kotlin.js.Promise
import PubNub as PubNubJs

actual class PubNub actual constructor(configuration: PNConfiguration) {
    private val jsPubNub: PubNubJs = createPubNub(configuration.userId.value, configuration.subscribeKey, configuration.publishKey)
    actual fun publish(
        channel: String,
        message: Any,
        meta: Any?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?
    ): Endpoint<PNPublishResult> {
        val params = Any().asDynamic()
        params.message = "myMessage"
        params.channel = "myChannel"
        val result: Promise<dynamic> = jsPubNub.publish(params)
        return result.then { res: dynamic ->
            PNPublishResult(res.timetoken.toString().toLong())
        }.toKmp()
    }
}

actual class PNPublishResult(
    actual val timetoken: Long
)

private fun <T> Promise<T>.toKmp() : Endpoint<T>{
    return object : Endpoint<T> {
        override fun async(callback: (T) -> Unit) {
            then(callback)
        }
    }
}