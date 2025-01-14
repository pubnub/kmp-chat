@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.message.BaseMessageImpl
import com.pubnub.kmp.then
import kotlin.js.Promise

@JsExport
@JsName("ThreadMessage")
class ThreadMessageJs internal constructor(internal val threadMessage: ThreadMessage, chatJs: ChatJs) : MessageJs(threadMessage, chatJs) {
    val parentChannelId by threadMessage::parentChannelId

    fun pinToParentChannel(): Promise<ChannelJs> {
        return threadMessage.pinToParentChannel().then { it.asJs(chatJs) }.asPromise()
    }

    fun unpinFromParentChannel(): Promise<ChannelJs> {
        return threadMessage.unpinFromParentChannel().then { it.asJs(chatJs) }.asPromise()
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(threadMessages: Array<ThreadMessageJs>, callback: (Array<ThreadMessageJs>) -> Unit): () -> Unit {
            val chatJs = threadMessages.first().chatJs
            return BaseMessageImpl.streamUpdatesOn(threadMessages.map { it.threadMessage }) { messages ->
                callback(messages.map { it.asJs(chatJs) }.toTypedArray())
            }::close
        }
    }
}

internal fun ThreadMessage.asJs(chat: ChatJs) = ThreadMessageJs(this, chat)
