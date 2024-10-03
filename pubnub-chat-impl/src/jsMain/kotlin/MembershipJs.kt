@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.Membership
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.kmp.then
import kotlin.js.Promise

@JsExport
@JsName("Membership")
class MembershipJs internal constructor(val membership: Membership) {
    val channel: ChannelJs get() = membership.channel.asJs()
    val user: UserJs get() = membership.user.asJs()
    val custom get() = membership.custom?.toJsObject()
    val updated by membership::updated
    val eTag by membership::eTag

    val lastReadMessageTimetoken: String? get() = membership.lastReadMessageTimetoken?.toString()

    fun update(custom: dynamic): Promise<MembershipJs> {
        return membership.update(convertToCustomObject(custom?.custom))
            .then { it.asJs() }
            .asPromise()
    }

    fun streamUpdates(callback: (MembershipJs?) -> Unit): () -> Unit {
        return streamUpdatesOn(arrayOf(this)) {
            callback(it.firstOrNull())
        }
    }

    fun setLastReadMessage(message: MessageJs): Promise<MembershipJs> {
        return membership.setLastReadMessage(message.message).then { it.asJs() }.asPromise()
    }

    fun setLastReadMessageTimetoken(timetoken: String): Promise<MembershipJs> {
        return membership.setLastReadMessageTimetoken(timetoken.toLong()).then { it.asJs() }.asPromise()
    }

    fun getUnreadMessagesCount(): Promise<Any/* number | false */> {
        return membership.getUnreadMessagesCount().then {
            it?.toInt() ?: false
        }.asPromise()
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(memberships: Array<MembershipJs>, callback: (Array<MembershipJs>) -> Unit): () -> Unit {
            return MembershipImpl.streamUpdatesOn(memberships.map { it.membership }) {
                callback(it.map { it.asJs() }.toTypedArray())
            }.let {
                it::close
            }
        }
    }
}

internal fun Membership.asJs() = MembershipJs(this)
