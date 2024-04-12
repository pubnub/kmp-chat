@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.kmp.UserId
import kotlin.js.Promise


external class PubNub(configuration: dynamic) {
    fun publish(params: dynamic): Promise<dynamic>
}

@JsExport
fun createPubNub(userId: String, subscribeKey: String, publishKey: String): PubNub {
    val config: dynamic = Any()
    config.userId = userId
    config.subscribeKey = subscribeKey
    config.publishKey = publishKey
    return PubNub(config)
}


/// var a = new PubNub({ ... })