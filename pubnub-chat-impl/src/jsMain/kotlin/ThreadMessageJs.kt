@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.types.EntityChange
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
            return BaseMessage.streamUpdatesOn(threadMessages.map { it.threadMessage }) { messages: Collection<ThreadMessage> ->
                callback(messages.map { it.asJs(chatJs) }.toTypedArray())
            }::close
        }

        @JsStatic
        fun streamChangesOn(
            threadMessages: Array<ThreadMessageJs>,
            callback: (EntityChangeJs<ThreadMessageJs>) -> Unit
        ): () -> Unit {
            val chatJs = threadMessages.first().chatJs
            return BaseMessage.streamChangesOn(
                threadMessages.map { it.threadMessage }
            ) { change: EntityChange<ThreadMessage> ->
                when (change) {
                    is EntityChange.Updated -> callback(EntityChangeJs.Updated(change.entity.asJs(chatJs)))
                    is EntityChange.Removed -> callback(EntityChangeJs.Removed(change.id))
                }
            }::close
        }
    }
}

internal fun ThreadMessage.asJs(chat: ChatJs) = ThreadMessageJs(this, chat)
