@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.ChannelGroup
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import kotlin.js.Promise

@JsExport
@JsName("ChannelGroup")
@Suppress("UndocumentedPublicClass")
class ChannelGroupJs internal constructor(
    private val chatJs: ChatJs,
    private val channelGroup: ChannelGroup
) {
    val id: String by channelGroup::id

    fun listChannels(params: PubNub.GetAllMetadataParameters?): Promise<GetChannelsResponseJs> {
        return channelGroup.listChannels(
            params?.filter,
            extractSortKeys(params?.sort),
            params?.limit?.toInt(),
            params?.page?.toKmp()
        ).then { result ->
            createJsObject<GetChannelsResponseJs> {
                this.channels =
                    result.channels.map { it.asJs(this@ChannelGroupJs.chatJs) }.toTypedArray()
                this.page = metadataPage(result.next, result.prev)
                this.total = result.total
            }
        }.asPromise()
    }

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

    fun whoIsPresent(params: WhoIsPresentParams?): Promise<JsMap<Array<String>>> {
        return channelGroup.whoIsPresent(
            params?.limit ?: 1000,
            params?.offset
        ).then {
            it
                .mapKeys { entry -> entry.key.toString() }
                .mapValues { entry -> entry.value.toTypedArray() }
                .toJsMap()
        }.asPromise()
    }

    fun streamPresence(callback: (JsMap<Array<String>>) -> Unit): Promise<() -> Unit> {
        return channelGroup.streamPresence {
            callback(
                it
                    .mapKeys { entry -> entry.key.toString() }
                    .mapValues { entry -> entry.value.toTypedArray() }
                    .toJsMap()
            )
        }.let { autoCloseable ->
            autoCloseable::close.asFuture().asPromise()
        }
    }
}
