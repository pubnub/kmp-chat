@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.createJsonElement
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.types.EventContent

@JsExport
class CryptoUtils {
    companion object {
        @JsStatic
        fun decrypt(params: DecryptParams): MessageJs {
            val decryptedContentJs = params.decryptor(params.message.message.text)
            val decryptedContent: EventContent.TextMessageContent = PNDataEncoder.decode(createJsonElement(decryptedContentJs))

            val message = params.message.message as BaseMessage<*>
            val newMessage = message.copyWithContent(decryptedContent)
            return (newMessage as? ThreadMessage)?.asJs(params.chat) ?: newMessage.asJs(params.chat)
        }
    }
}

external interface DecryptParams {
    val chat: ChatJs
    val message: MessageJs
    val decryptor: (String) -> Any
}

@JsExport
val CryptoModule = PubNub.CryptoModule
