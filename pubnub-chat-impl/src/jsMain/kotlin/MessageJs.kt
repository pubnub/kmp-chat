@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.Message
import com.pubnub.chat.internal.message.BaseMessageImpl
import com.pubnub.kmp.then
import kotlin.js.Promise

@JsExport
@JsName("Message")
class MessageJs internal constructor(private val message: Message, chatJs: ChatJs) : BaseMessageJs(message, chatJs) {
    val hasThread by message::hasThread

    fun getThread(): Promise<ThreadChannelJs> {
        return message.getThread().then { it.asJs(chatJs) }.asPromise()
    }

    fun createThread(): Promise<ThreadChannelJs> {
        return message.createThread().then { it.asJs(chatJs) }.asPromise()
    }

    fun removeThread(): Promise<Array<Any>> {
        return message.removeThread().then {
            arrayOf(
                Any(),
                it.second?.asJs(chatJs)?.let { channelJs -> DeleteChannelResult(channelJs) }
                    ?: DeleteChannelResult(true)
            )
        }.asPromise()
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(messages: Array<MessageJs>, callback: (Array<MessageJs>) -> Unit): () -> Unit {
            val chatJs = messages.first().chatJs
            return BaseMessageImpl.streamUpdatesOn(messages.map { it.message }) { kmpMessages ->
                callback(kmpMessages.map { kmpMessage -> MessageJs(kmpMessage, chatJs) }.toTypedArray())
            }::close
        }
    }
}
