package compubnub.chat

import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.asList
import com.pubnub.api.asMap
import com.pubnub.api.asString
import com.pubnub.api.enums.PNLogVerbosity
import com.pubnub.api.models.consumer.access_manager.v3.ChannelGrant
import com.pubnub.api.models.consumer.access_manager.v3.UUIDGrant
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Chat
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.CustomPayloads
import com.pubnub.chat.config.LogLevel
import com.pubnub.chat.init
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.test.BaseIntegrationTest
import com.pubnub.test.Keys
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ChatIntegrationTest : BaseIntegrationTest() {
    @Test
    fun canInitializeChatWithLogLevel() {
        val chatConfig = ChatConfiguration(logLevel = LogLevel.OFF)
        val pnConfiguration =
            PNConfiguration.builder(userId = UserId("myUserId"), subscribeKey = "mySubscribeKey").build()

        Chat.init(chatConfig, pnConfiguration).async { result: Result<Chat> ->
            result.onSuccess { chat: Chat ->
                println("Chat successfully initialized having logLevel: ${chatConfig.logLevel}")
            }.onFailure { exception: PubNubException ->
                println("Exception initialising chat: ${exception.message}")
            }
        }
    }

    @Test
    fun test_storeUserActivityInterval_and_storeUserActivityTimestamps() = runTest {
        val chatConfig = ChatConfiguration(storeUserActivityInterval = 100.seconds, storeUserActivityTimestamps = true)

        val chat: Chat = Chat.init(chatConfig, config).await()
        val user = chat.getUser(chat.currentUser.id).await()

        assertTrue(user?.custom?.get("lastActiveTimestamp") != null)
    }

    @Test
    fun shouldReceiveExceptionWhenInitializingChatWithoutValidTokenWhenPamEnabled() = runTest {
        val exception: PubNubException = assertFailsWith<PubNubException> {
            Chat.init(ChatConfiguration(), configPamClient).await()
        }

        assertEquals(403, exception.statusCode)
    }

    @Test
    fun canInitializeChatWhenPamEnableAndTokenSet() = runTest {
        val clientUserId = randomString()

        val serverChat = Chat.init(ChatConfiguration(), configPamServer).await()
        val token = serverChat.pubNub.grantToken(
            ttl = 1,
            channels = listOf(ChannelGrant.name(get = true, name = "anyChannelForNow")),
            uuids = listOf(UUIDGrant.id(id = clientUserId, get = true, update = true)) // this is important
        ).await().token

        val configPamClient: PNConfiguration = PNConfiguration.builder(UserId(clientUserId), Keys.pamSubKey) {
            publishKey = Keys.pamPubKey
            logVerbosity = PNLogVerbosity.BODY
            authToken = token
        }.build()

        val clientChat = Chat.init(ChatConfiguration(), configPamClient).await()

        assertEquals(clientUserId, clientChat.currentUser.id)
        assertEquals(clientChat.pubNub.getToken(), token)
    }

    @Test
    fun canInitializeChatWithCustomPayloadAndCustomActions() {
        var chat: Chat
        val customPayloads: CustomPayloads = CustomPayloads(
            getMessagePublishBody = { content, channelId, defaultMessagePublishBody ->
                // Define which channel should use custom payload
                if (channelId == "support-channel") {
                    mapOf(
                        "custom" to mapOf(
                            "payload" to mapOf(
                                "text" to content.text
                            )
                        ),
                        "files" to content.files
                    )
                } else {
                    // The rest of the channels will use the default Chat SDK message body structure
                    defaultMessagePublishBody(content)
                }
            },
            getMessageResponseBody = { json, channelId, defaultMessageResponseBody ->
                if (channelId === "support-channel") {
                    EventContent.TextMessageContent(
                        json.asMap()?.get("custom")?.asMap()?.get("payload")?.asMap()?.get("text")?.asString()!!,
                        json.asMap()?.get("files")?.asList()?.map {
                            File(
                                it.asMap()?.get("name")?.asString()!!,
                                it.asMap()?.get("id")?.asString()!!,
                                it.asMap()?.get("url")?.asString()!!,
                                it.asMap()?.get("type")?.asString(),
                            )
                        }
                    )
                } else {
                    defaultMessageResponseBody(json)
                }
            },
            // Override the default Chat SDK action type for editing a message ("edited") with your own name
            editMessageActionName = "updated",
            // Override the default Chat SDK action type for deleting a message ("deleted") with your own name
            deleteMessageActionName = "removed"
        )
        val chatConfig = ChatConfiguration(logLevel = LogLevel.OFF, customPayloads = customPayloads)
        val builder = PNConfiguration.builder(userId = UserId("myUserId"), subscribeKey = "mySubscribekey") {
            publishKey = "myPublishKey"
        }
        val pnConfiguration = builder.build()
        Chat.init(chatConfig, pnConfiguration).async { result: Result<Chat> ->
            result.onSuccess { createdChat: Chat ->
                chat = createdChat
            }.onFailure { exception: PubNubException ->
                println("Exception initialising chat: ${exception.message}")
            }
        }
    }
}
