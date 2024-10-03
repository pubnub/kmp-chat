@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.kmp.then
import kotlin.js.Promise

@JsExport
@JsName("ThreadMessage")
class ThreadMessageJs internal constructor(internal val threadMessage: ThreadMessage) : MessageJs(threadMessage) {
    val parentChannelId by threadMessage::parentChannelId

    fun pinToParentChannel(): Promise<ChannelJs> {
        return threadMessage.pinToParentChannel().then { it.asJs() }.asPromise()
    }

    fun unpinFromParentChannel(): Promise<ChannelJs> {
        return threadMessage.unpinFromParentChannel().then { it.asJs() }.asPromise()
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(threadMessages: Array<ThreadMessageJs>, callback: (Array<ThreadMessageJs>) -> Unit): () -> Unit {
            return BaseMessage.streamUpdatesOn(threadMessages.map { it.threadMessage }) { messages ->
                callback(messages.map(ThreadMessage::asJs).toTypedArray())
            }::close
        }
    }
}

internal fun ThreadMessage.asJs() = ThreadMessageJs(this)
