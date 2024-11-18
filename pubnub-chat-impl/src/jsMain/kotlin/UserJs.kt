@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.chat.User
import com.pubnub.chat.internal.UserImpl
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import com.pubnub.kmp.toMap
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("User")
class UserJs internal constructor(internal val user: User) : UserFields {
    val active: Boolean get() = user.active

    override val id get() = user.id
    override val name get() = user.name
    override val externalId get() = user.externalId
    override val profileUrl get() = user.profileUrl
    override val email get() = user.email
    override val custom: Any?
        get() = user.custom?.toJsMap() // TODO need to convert map values recursively?
    override val status get() = user.status
    override val type get() = user.type
    val updated get() = user.updated
    val lastActiveTimestamp get() = user.lastActiveTimestamp?.toInt()

    fun update(data: UserFields): Promise<UserJs> {
        return user.update(
            data.name,
            data.externalId,
            data.profileUrl,
            data.email,
            convertToCustomObject(data.custom), // TODO
            data.status,
            data.type
        ).then {
            UserJs(it)
        }.asPromise()
    }

    fun delete(options: DeleteParameters?): Promise</*true | UserJs*/Any> { // TODO
        return user.delete(options?.soft ?: false).then {
            it?.asJs() ?: true
        }.asPromise()
    }

    fun streamUpdates(callback: (user: UserJs?) -> Unit): () -> Unit {
        val closeable = user.streamUpdates {
            callback(it?.asJs())
        }
        return closeable::close
    }

    fun wherePresent(): Promise<Array<String>> {
        return user.wherePresent().then { it.toTypedArray() }.asPromise()
    }

    fun isPresentOn(channelId: String): Promise<Boolean> {
        return user.isPresentOn(channelId).asPromise()
    }

    fun getMemberships(params: PubNub.GetMembershipsParametersv2?): Promise<MembershipsResponseJs> {
        val page = params?.page
        return user.getMemberships(
            params?.limit?.toInt(),
            page.toKmp(),
            params?.filter,
            params?.sort?.unsafeCast<JsMap<String>>()?.toMap()?.map {
                val fieldName = it.key
                val direction = it.value
                if (direction == "asc") {
                    PNSortKey.PNAsc(PNMembershipKey.valueOf(fieldName))
                } else {
                    PNSortKey.PNDesc(PNMembershipKey.valueOf(fieldName))
                }
            } ?: listOf()
        ).then {
            createJsObject<MembershipsResponseJs> {
                this.page = MetadataPage(it.next, it.prev)
                this.total = it.total
                this.memberships = it.memberships.map(::MembershipJs).toTypedArray()
                this.status = it.status
            }
        }.asPromise()
    }

    fun setRestrictions(
        channel: ChannelJs,
        params: RestrictionJs,
    ): Promise<Any> {
        return user.setRestrictions(
            channel.channel,
            params.ban ?: false,
            params.mute ?: false,
            params.reason?.toString()
        ).asPromise()
    }

    fun getChannelRestrictions(channel: ChannelJs): Promise<RestrictionJs> {
        return user.getChannelRestrictions(channel.channel).then {
            it.asJs()
        }.asPromise()
    }

    fun getChannelsRestrictions(params: PubNub.GetChannelMembersParameters?): Promise<GetRestrictionsResponseJs> {
        return user.getChannelsRestrictions(
            params?.limit?.toInt(),
            params?.page?.toKmp(),
            extractSortKeys(params?.sort),
        ).then { result ->
            result.toJs()
        }.asPromise()
    }

    fun toJSON(): Any {
        return json(
            "active" to active,
            "id" to id,
            "name" to name,
            "externalId" to externalId,
            "profileUrl" to profileUrl,
            "email" to email,
            "custom" to custom,
            "status" to status,
            "type" to type,
            "updated" to updated,
            "lastActiveTimestamp" to lastActiveTimestamp
        )
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(users: Array<UserJs>, callback: (users: Array<UserJs>) -> Unit): () -> Unit {
            val closeable = UserImpl.streamUpdatesOn(users.map { jsUser -> jsUser.user }) {
                callback(it.map { kmpUser -> UserJs(kmpUser) }.toTypedArray())
            }
            return closeable::close
        }
    }
}

internal fun User.asJs() = UserJs(this)
