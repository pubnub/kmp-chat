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

class ChatConfigurationIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun custom_payloads_send_receive_msgs() = runTest {
        val chat = ChatImpl(
            ChatConfiguration(
                customPayloads = CustomPayloads(
                    getMessagePublishBody = { content, _, _ ->
                        mapOf(
                            "custom" to mapOf(
                                "payload" to mapOf(
                                    "text" to content.text
                                )
                            ),
                            "files" to content.files,
                        )
                    },
                    getMessageResponseBody = { json: JsonElement, _, _ ->
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
                    }
                )
            ),
            pubnub
        ).initialize().await()
        val channel = chat.createPublicConversation(randomString()).await()
        val messageText = randomString()
        val message = CompletableDeferred<Message>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                unsubscribe = channel.onMessageReceived {
                    message.complete(it)
                }
            }
            channel.sendText(messageText).await()
            assertEquals(messageText, message.await().text)
            unsubscribe?.close()
        }
    }

    // todo flaky for iOS on command line execution
    @Test
    fun custom_payloads_send_receive_msgs_single_channel() = runTest {
        val chat = ChatImpl(
            ChatConfiguration(
                customPayloads = CustomPayloads(
                    getMessagePublishBody = { content, channelId, default ->
                        if (channelId == channel01.id) {
                            mapOf(
                                "custom" to mapOf(
                                    "payload" to mapOf(
                                        "text" to content.text
                                    )
                                ),
                                "files" to content.files,
                            )
                        } else {
                            default(content)
                        }
                    },
                    getMessageResponseBody = { json: JsonElement, channelId, default ->
                        if (channelId == channel01.id) {
                            EventContent.TextMessageContent(
                                json.asMap()?.get("custom")?.asMap()?.get("payload")?.asMap()?.get("text")
                                    ?.asString()!!,
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
                            default(json)
                        }
                    }
                )
            ),
            pubnub
        ).initialize().await()
        chat.createPublicConversation().await()
        chat.createDirectConversation(someUser).await()
        val messageText = randomString()
        val message = CompletableDeferred<Message>()
        val message2 = CompletableDeferred<Message>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id, channel02.id)) {
                unsubscribe = channel01.onMessageReceived {
                    message.complete(it)
                }
                unsubscribe = channel02.onMessageReceived {
                    message2.complete(it)
                }
            }
            channel01.sendText(messageText).await()
            channel02.sendText(messageText).await()
            assertEquals(messageText, message.await().text)
            unsubscribe?.close()
        }
    }
}
