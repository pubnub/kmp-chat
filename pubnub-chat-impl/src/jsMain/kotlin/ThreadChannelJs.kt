@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.Event
import com.pubnub.chat.ThreadChannel
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.js.Promise

@JsExport
@JsName("ThreadChannel")
class ThreadChannelJs internal constructor(internal val threadChannel: ThreadChannel, chatJs: ChatJs) : ChannelJs(threadChannel, chatJs) {
    val parentChannelId by threadChannel::parentChannelId

    override fun pinMessage(message: MessageJs): Promise<ChannelJs> {
        return channel.pinMessage(message.message).then { it.asJs(chatJs) }.asPromise()
    }

    override fun unpinMessage(): Promise<ChannelJs> {
        return channel.unpinMessage().then { it.asJs(chatJs) }.asPromise()
    }

    fun pinMessageToParentChannel(message: ThreadMessageJs): Promise<ChannelJs> {
        return threadChannel.pinMessageToParentChannel(message.threadMessage).then { it.asJs(chatJs) }.asPromise()
    }

    fun unpinMessageFromParentChannel(): Promise<ChannelJs> {
        return threadChannel.unpinMessageFromParentChannel().then { it.asJs(chatJs) }.asPromise()
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

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
internal fun Event<*>.toJs(chatJs: ChatJs): EventJs {
    return EventJs(
        chatJs,
        timetoken.toString(),
        payload::class.serializer().descriptor.serialName,
        payload.toJsObject().apply {
            delete(this.asDynamic()["type"])
        },
        channelId,
        userId
    )
}

internal fun ThreadChannel.asJs(chat: ChatJs) = ThreadChannelJs(this, chat)
