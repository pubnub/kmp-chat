package compubnub.chat

import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Chat
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.LogLevel
import com.pubnub.chat.init
import kotlin.test.Test

class ChatIntegrationTest {

    @Test
    fun canInitializeChatWithLogLevel() {
        val chatConfig = ChatConfiguration(logLevel = LogLevel.INFO)
        val pnConfiguration = PNConfiguration.builder(userId = UserId("myUserId"), subscribeKey = "mySubscribeKey").build()

        Chat.init(chatConfig, pnConfiguration).async { result: Result<Chat> ->
            result.onSuccess { chat: Chat ->
                println("Chat successfully initialized having logLevel: ${chatConfig.logLevel}")
            }.onFailure { exception: PubNubException ->
                println("Exception initialising chat: ${exception.message}")
            }
        }

    }
}
