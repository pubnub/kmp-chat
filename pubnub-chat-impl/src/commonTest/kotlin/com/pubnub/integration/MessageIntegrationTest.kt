package com.pubnub.integration

import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.chat.Message
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.MessageActionType
import com.pubnub.kmp.createCustomObject
import com.pubnub.test.await
import com.pubnub.test.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun streamUpdatesOn() = runTest(timeout = defaultTimeout) {
        chat.createChannel(
            channel01.id,
            channel01.name,
            channel01.description,
            channel01.custom?.let { createCustomObject(it) },
            channel01.type,
            channel01.status
        ).await()

        val tt1 = channel01.sendText("message1").await()
        val tt2 = channel01.sendText("message2").await()
        delayInMillis(1000)

        val message1 = channel01.getMessage(tt1.timetoken).await()!!
        val message2 = channel01.getMessage(tt2.timetoken).await()!!

        val newText = "newText"
        val expectedUpdates = listOf<List<Message>>(
            listOf(message1.asImpl().copy(content = EventContent.TextMessageContent(newText)), message2),
            listOf(
                message1.asImpl().copy(content = EventContent.TextMessageContent(newText)),
                message2.asImpl().copy(
                    content = EventContent.TextMessageContent(
                        newText
                    )
                )
            ),
            listOf(
                message1.asImpl().copy(content = EventContent.TextMessageContent(newText)),
                message2.asImpl().copy(
                    content = EventContent.TextMessageContent(newText),
                    actions = getDeletedActionMap()
                )
            ),
            listOf(
                message1.asImpl().copy(
                    content = EventContent.TextMessageContent(newText),
                    actions = getDeletedActionMap()
                ),
                message2.asImpl().copy(
                    content = EventContent.TextMessageContent(newText),
                    actions = getDeletedActionMap()
                )
            ),
        )
        val actualUpdates = mutableListOf<List<Message>>()

        pubnub.test(backgroundScope, checkAllEvents = false) {
            var dispose: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel01.id)) {
                dispose = BaseMessage.streamUpdatesOn(listOf(message1, message2)) { messages ->
                    actualUpdates.add(messages.sortedBy { it.timetoken })
                }
            }

            message1.editText(newText).await()
            message2.editText(newText).await()
            message2.delete(true).await()
            message1.delete(true).await()
            delayInMillis(500)
            message1.delete().await()
            message2.delete().await()

            delayInMillis(1000)
            dispose?.close()
        }
        assertEquals(
            expectedUpdates.map { it.map { Triple(it.timetoken, it.text, it.deleted) } },
            actualUpdates.map { it.map { Triple(it.timetoken, it.text, it.deleted) } }
        )
    }

    private fun getDeletedActionMap() = mapOf(
        MessageActionType.DELETED.toString() to mapOf(
            MessageActionType.DELETED.toString() to listOf(
                PNFetchMessageItem.Action("anything", 123L)
            )
        )
    )
}

private fun Message.asImpl(): MessageImpl {
    return this as MessageImpl
}
