@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.Membership
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("Membership")
class MembershipJs internal constructor(internal val membership: Membership, internal val chatJs: ChatJs) {
    val channel: ChannelJs get() = membership.channel.asJs(chatJs)
    val user: UserJs get() = membership.user.asJs(chatJs)
    val custom get() = membership.custom?.toJsMap()
    val updated by membership::updated
    val eTag by membership::eTag

    val lastReadMessageTimetoken: String? get() = membership.lastReadMessageTimetoken?.toString()

    fun update(custom: dynamic): Promise<MembershipJs> {
        return membership.update(convertToCustomObject(custom?.custom))
            .then { it.asJs(chatJs) }
            .asPromise()
    }

    fun streamUpdates(callback: (MembershipJs?) -> Unit): () -> Unit {
        return streamUpdatesOn(arrayOf(this)) {
            callback(it.firstOrNull())
        }
    }

    fun setLastReadMessage(message: MessageJs): Promise<MembershipJs> {
        return membership.setLastReadMessage(message.message).then { it.asJs(chatJs) }.asPromise()
    }

    fun setLastReadMessageTimetoken(timetoken: String): Promise<MembershipJs> {
        return membership.setLastReadMessageTimetoken(timetoken.toLong()).then { it.asJs(chatJs) }.asPromise()
    }

    fun getUnreadMessagesCount(): Promise<GetUnreadMessagesCountResult> {
        return membership.getUnreadMessagesCount().then {
            if (it != null) {
                GetUnreadMessagesCountResult(it.toInt())
            } else {
                GetUnreadMessagesCountResult(false)
            }
        }.asPromise()
    }

    fun toJSON(): Json {
        return json(
            "channel" to channel,
            "user" to user,
            "custom" to custom,
            "updated" to updated,
            "eTag" to eTag,
            "lastReadMessageTimetoken" to lastReadMessageTimetoken
        )
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(memberships: Array<MembershipJs>, callback: (Array<MembershipJs>) -> Unit): () -> Unit {
            val chatJs = memberships.first().chatJs
            return MembershipImpl.streamUpdatesOn(memberships.map { it.membership }) {
                callback(it.map { it.asJs(chatJs) }.toTypedArray())
            }.let {
                it::close
            }
        }
    }
}

internal fun Membership.asJs(chat: ChatJs) = MembershipJs(this, chat)
