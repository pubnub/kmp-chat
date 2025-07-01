import com.pubnub.chat.ChannelGroup
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ChannelGroup")
class ChannelGroupJs internal constructor(
    private val chatJs: ChatJs,
    private val channelGroup: ChannelGroup
) {
    val id: String by channelGroup::id

    fun addChannels(channels: Array<ChannelJs>): Promise<Any> {
        return channelGroup.addChannels(channels.map { it.channel }).asPromise()
    }

    fun addChannelIdentifiers(ids: Array<String>): Promise<Any> {
        return channelGroup.addChannelIdentifiers(ids.asList()).asPromise()
    }

    fun removeChannels(channels: Array<ChannelJs>): Promise<Any> {
        return channelGroup.removeChannels(channels.map { it.channel }).asPromise()
    }

    fun removeChannelIdentifiers(ids: Array<String>): Promise<Any> {
        return channelGroup.removeChannelIdentifiers(ids.asList()).asPromise()
    }

    fun connect(callback: (MessageJs) -> Unit): () -> Unit {
        return channelGroup.connect {
            callback(it.asJs(chatJs))
        }::close
    }
}
