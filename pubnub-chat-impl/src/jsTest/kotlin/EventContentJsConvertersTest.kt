import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.toMap
import kotlin.test.Test
import kotlin.test.assertEquals

class EventContentJsConvertersTest {
    @Test
    fun toJsTextMessage_encodes_text_message_content_with_concrete_serializer() {
        val payload = EventContent.TextMessageContent("hello").toJsTextMessage()
        val payloadMap = payload.toMap()

        assertEquals("hello", payloadMap["text"])
        assertEquals(
            EventContent.TextMessageContent.serializer().descriptor.serialName,
            payloadMap["type"]
        )
    }

    @Test
    fun eventToJs_encodes_receipt_payload_with_concrete_serializer() {
        val event = object : Event<EventContent.Receipt> {
            override val chat: Chat = js("{}").unsafeCast<Chat>()
            override val timetoken: Long = 456L
            override val payload: EventContent.Receipt = EventContent.Receipt(123L)
            override val channelId: String = "channel"
            override val userId: String = "user"
        }

        val jsEvent = event.toJs(js("{}").unsafeCast<ChatJs>())
        val payloadMap = jsEvent.payload.unsafeCast<JsMap<Any?>>().toMap()

        assertEquals(EventContent.Receipt.serializer().descriptor.serialName, jsEvent.type)
        assertEquals("123", payloadMap["messageTimetoken"])
    }
}
