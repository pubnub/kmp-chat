@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.Event
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.internal.TYPE_OF_MESSAGE
import com.pubnub.chat.internal.TYPE_OF_MESSAGE_IS_CUSTOM
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import kotlin.js.Promise

@JsExport
@JsName("ThreadChannel")
class ThreadChannelJs internal constructor(internal val threadChannel: ThreadChannel, chatJs: ChatJs) : ChannelJs(threadChannel, chatJs) {
    val parentChannelId by threadChannel::parentChannelId

    override fun pinMessage(message: MessageJs): Promise<ThreadChannelJs> {
        return threadChannel.pinMessage(message.message).then { it.asJs(chatJs) }.asPromise()
    }

    override fun unpinMessage(): Promise<ThreadChannelJs> {
        return threadChannel.unpinMessage().then { it.asJs(chatJs) }.asPromise()
    }

    override fun update(data: ChannelFields): Promise<ThreadChannelJs> {
        return threadChannel.update(
            data.name,
            data.custom?.let { convertToCustomObject(it) },
            data.description,
            data.status,
            data.type?.let { ChannelType.from(it) }
        ).then {
            it.asJs(chatJs)
        }.asPromise()
    }

    override fun getMessage(timetoken: String): Promise<ThreadMessageJs> {
        return threadChannel.getMessage(timetoken.tryLong()!!).then { it!!.asJs(chatJs) }.asPromise()
    }

    override fun getPinnedMessage(): Promise<MessageJs?> {
        return threadChannel.getPinnedMessage().then { it?.asJs(chatJs) }.asPromise()
    }

    fun pinMessageToParentChannel(message: ThreadMessageJs): Promise<ChannelJs> {
        return threadChannel.pinMessageToParentChannel(message.threadMessage).then { it.asJs(chatJs) }.asPromise()
    }

    fun unpinMessageFromParentChannel(): Promise<ChannelJs> {
        return threadChannel.unpinMessageFromParentChannel().then { it.asJs(chatJs) }.asPromise()
    }

    fun onThreadMessageReceived(callback: (ThreadMessageJs) -> Unit): () -> Unit {
        return threadChannel.onThreadMessageReceived { callback(it.asJs(chatJs)) }::close
    }

    fun onThreadChannelUpdated(callback: (ThreadChannelJs) -> Unit): () -> Unit {
        return threadChannel.onThreadChannelUpdated { callback(it.asJs(chatJs)) }::close
    }

    override fun getHistory(params: dynamic): Promise<HistoryResponseJs> {
        return threadChannel.getHistory(
            params?.startTimetoken?.toString()?.toLong(),
            params?.endTimetoken?.toString()?.toLong(),
            params?.count?.toString()?.toInt() ?: 25
        ).then { result ->
            createJsObject<HistoryResponseJs> {
                this.isMore = result.isMore
                this.messages = result.messages.map { it.asJs(chatJs) }.toTypedArray()
            }
        }.asPromise()
    }
}

external fun delete(p: dynamic): Boolean

internal fun Event<*>.toJs(chatJs: ChatJs): EventJs {
    val customPayload = payload as? EventContent.Custom
    return if (customPayload != null) {
        EventJs(
            chatJs,
            timetoken.toString(),
            TYPE_OF_MESSAGE_IS_CUSTOM,
            customPayload.data.toJsObject(),
            channelId,
            userId
        )
    } else {
        EventJs(
            chatJs,
            timetoken.toString(),
            payload.jsEventType(),
            payload.toJsEventPayload().apply {
                delete(this.asDynamic()[TYPE_OF_MESSAGE])
            },
            channelId,
            userId
        )
    }
}

internal fun ThreadChannel.asJs(chat: ChatJs) = ThreadChannelJs(this, chat)
