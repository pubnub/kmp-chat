@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.kmp.then
import kotlin.js.Promise

@JsExport
@JsName("ThreadMessage")
class ThreadMessageJs internal constructor(internal val threadMessage: ThreadMessage, chatJs: ChatJs) : MessageJs(threadMessage, chatJs) {
    val parentChannelId by threadMessage::parentChannelId

    override fun editText(newText: String): Promise<ThreadMessageJs> {
        return threadMessage.editText(newText).then { it.asJs(chatJs) }.asPromise()
    }

    override fun toggleReaction(reaction: String): Promise<ThreadMessageJs> {
        return threadMessage.toggleReaction(reaction).then { it.asJs(chatJs) }.asPromise()
    }

    override fun restore(): Promise<ThreadMessageJs> {
        return threadMessage.restore().then { it.asJs(chatJs) }.asPromise()
    }

    fun pinToParentChannel(): Promise<ChannelJs> {
        return threadMessage.pinToParentChannel().then { it.asJs(chatJs) }.asPromise()
    }

    fun unpinFromParentChannel(): Promise<ChannelJs> {
        return threadMessage.unpinFromParentChannel().then { it.asJs(chatJs) }.asPromise()
    }

    fun onThreadMessageUpdated(callback: (message: ThreadMessageJs) -> Unit): () -> Unit {
        val closeable = threadMessage.onThreadMessageUpdated {
            callback(it.asJs(chatJs))
        }
        return closeable::close
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(threadMessages: Array<ThreadMessageJs>, callback: (Array<ThreadMessageJs>) -> Unit): () -> Unit {
            val chatJs = threadMessages.first().chatJs
            return BaseMessage.streamUpdatesOn(threadMessages.map { it.threadMessage }) { messages ->
                callback(messages.map { it.asJs(chatJs) }.toTypedArray())
            }::close
        }
    }
}

internal fun ThreadMessage.asJs(chat: ChatJs) = ThreadMessageJs(this, chat)
