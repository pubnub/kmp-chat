import com.pubnub.api.JsonElement
import com.pubnub.api.asList
import com.pubnub.api.asMap
import com.pubnub.api.asString
import com.pubnub.chat.Message
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.CustomPayloads
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.integration.BaseChatIntegrationTest
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChatConfigurationIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun custom_payloads_send_receive_msgs() = runTest {
        val chat = ChatImpl(
            ChatConfiguration(
                customPayloads = CustomPayloads(
                    getMessagePublishBody = { content, channelId ->
                        mapOf(
                            "custom" to mapOf(
                                "payload" to mapOf(
                                    "text" to content.text
                                )
                            ),
                            "files" to content.files,
//                        "type" to "text"
                        )
                    },
                    getMessageResponseBody = { json: JsonElement ->
                        EventContent.TextMessageContent(
                            json.asMap()?.get("custom")?.asMap()?.get("payload")?.asMap()?.get("text")?.asString()!!,
                            json.asList()?.map {
                                File(
                                    it.asMap()?.get("name")?.asString()!!,
                                    it.asMap()?.get("id")?.asString()!!,
                                    it.asMap()?.get("url")?.asString()!!,
                                    it.asMap()?.get("type")?.asString(),
                                )
                            }
                        )
                    }
                )
            ),
            pubnub
        ).initialize().await()
        val channel = chat.createChannel(randomString()).await()
        val messageText = randomString()
        val message = CompletableDeferred<Message>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe {
                unsubscribe = channel.connect {
                    message.complete(it)
                }
            }
            channel.sendText(messageText).await()
            assertEquals(messageText, message.await().text)
            assertFalse(channel.getMembers().await().members.any { it.user.id == chat.currentUser.id })
            unsubscribe?.close()
        }
    }
}
