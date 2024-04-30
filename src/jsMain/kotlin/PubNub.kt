@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.com.pubnub.kmp.Chat
import com.pubnub.kmp.ChatImpl
import com.pubnub.kmp.ChatConfig
import com.pubnub.kmp.User
import kotlin.js.Promise


external class PubNub(configuration: dynamic) {
    fun publish(params: dynamic): Promise<dynamic>
    val objects: dynamic
}


@JsExport
fun createPubNub(userId: String, subscribeKey: String, publishKey: String): PubNub {
    val config: dynamic = Any()
    config.userId = userId
    config.subscribeKey = subscribeKey
    config.publishKey = publishKey
    return PubNub(config)
}

@JsExport
class ChatJs(private val chatConfig: ChatConfig) {
    private val chat: Chat = ChatImpl(chatConfig)

    fun createUser(id: String,
                   name: String?,
                   externalId: String? = null,
                   profileUrl: String? = null,
                   email: String? = null,
                   custom: Any?  = null,
                   status: String? = null,
                   type: String? = null,): Promise<User> {
        return Promise { resolve: (User) -> Unit, reject: (Throwable) -> Unit ->
            chat.createUser(id, name, externalId, profileUrl,email,custom, status, type) { result: Result<User> ->
                result.onSuccess { resolve(it) }.onFailure { reject(it) }
            }
        }
    }
}




/// var a = new PubNub({ ... })