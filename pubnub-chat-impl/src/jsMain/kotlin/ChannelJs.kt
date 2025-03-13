@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.Channel
import com.pubnub.chat.internal.channel.BaseChannelImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import kotlin.js.Promise

@JsExport
@JsName("Channel")
class ChannelJs internal constructor(private val channel: Channel, chatJs: ChatJs) : BaseChannelJs(channel, chatJs) {
    fun join(callback: (BaseMessageJs) -> Unit, params: PubNub.SetMembershipsParameters?): Promise<JoinResultJs> {
        return channel.join { callback(it.asJs(chatJs)) }.then {
            createJsObject<JoinResultJs> {
                membership = it.membership.asJs(chatJs)
                disconnect = it.disconnect?.let { autoCloseable -> autoCloseable::close } ?: {}
            }
        }.asPromise()
    }

    fun leave(): Promise<Boolean> {
        return channel.leave().then { true }.asPromise()
    }

    fun getMembers(params: PubNub.GetChannelMembersParameters?): Promise<MembersResponseJs> {
        return channel.getMembers(
            params?.limit?.toInt() ?: 100,
            params?.page?.toKmp(),
            params?.filter,
            extractSortKeys(params?.sort)
        ).then { result ->
            createJsObject<MembersResponseJs> {
                this.page = MetadataPage(result.next, result.prev)
                this.total = result.total
                this.status = result.status
                this.members = result.members.map { it.asJs(chatJs) }.toTypedArray()
            }
        }.asPromise()
    }

    fun invite(user: UserJs): Promise<MembershipJs> {
        return channel.invite(user.user).then { it.asJs(chatJs) }.asPromise()
    }

    fun inviteMultiple(users: Array<UserJs>): Promise<Array<MembershipJs>> {
        return channel.inviteMultiple(users.map { it.user })
            .then { memberships ->
                memberships.map { it.asJs(chatJs) }.toTypedArray()
            }.asPromise()
    }

    fun streamReadReceipts(callback: (JsMap<Array<String>>) -> Unit): Promise<() -> Unit> {
        return channel.streamReadReceipts {
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

    companion object {
        @JsStatic
        fun streamUpdatesOn(channels: Array<ChannelJs>, callback: (Array<ChannelJs>) -> Unit): () -> Unit {
            val chatJs = channels.first().chatJs
            val closeable = BaseChannelImpl.streamUpdatesOn(
                channels.map { jsChannel -> jsChannel.channel },
                ChannelImpl::fromDTO
            ) {
                callback(it.map { kmpChannel -> ChannelJs(kmpChannel, chatJs) }.toTypedArray())
            }
            return closeable::close
        }
    }
}

internal fun Channel.asJs(chat: ChatJs) = ChannelJs(this, chat)
