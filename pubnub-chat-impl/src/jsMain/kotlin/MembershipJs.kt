@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.Membership
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.types.EntityChange
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
    val status by membership::status
    val type by membership::type

    val lastReadMessageTimetoken: String? get() = membership.lastReadMessageTimetoken?.toString()

    fun update(custom: UpdateMembershipParams?): Promise<MembershipJs> {
        return membership.update(custom?.custom?.let { convertToCustomObject(it) })
            .then { it.asJs(chatJs) }
            .asPromise()
    }

    fun streamUpdates(callback: (MembershipJs?) -> Unit): () -> Unit {
        return streamUpdatesOn(arrayOf(this)) { memberships: Array<MembershipJs> ->
            callback(memberships.firstOrNull())
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
            "status" to status,
            "type" to type,
            "lastReadMessageTimetoken" to lastReadMessageTimetoken
        )
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(memberships: Array<MembershipJs>, callback: (Array<MembershipJs>) -> Unit): () -> Unit {
            val chatJs = memberships.first().chatJs
            return MembershipImpl.streamUpdatesOn(memberships.map { it.membership }) { kmpMemberships: Collection<Membership> ->
                callback(kmpMemberships.map { it.asJs(chatJs) }.toTypedArray())
            }.let {
                it::close
            }
        }

        @JsStatic
        fun streamUpdatesOnWithEntityChange(
            memberships: Array<MembershipJs>,
            callback: (EntityChangeJs<MembershipJs>) -> Unit
        ): () -> Unit {
            val chatJs = memberships.first().chatJs
            return MembershipImpl.streamUpdatesOnWithEntityChange(memberships.map { it.membership }) { change: EntityChange<Membership> ->
                when (change) {
                    is EntityChange.Updated -> callback(EntityChangeJs.Updated(change.entity.asJs(chatJs)))
                    is EntityChange.Removed -> callback(EntityChangeJs.Removed(change.id))
                }
            }.let {
                it::close
            }
        }
    }
}

internal fun Membership.asJs(chat: ChatJs) = MembershipJs(this, chat)
