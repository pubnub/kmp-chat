@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.UploadableImpl
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import com.pubnub.kmp.toMap
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("Channel")
open class ChannelJs internal constructor(internal val channel: Channel) : ChannelFields {
    override val id: String get() = channel.id
    override val name: String? get() = channel.name
    override val custom: Any? get() = channel.custom?.toJsMap() // todo recursive?
    override val description: String? get() = channel.description
    override val updated: String? get() = channel.updated
    override val status: String? get() = channel.status
    override val type: String? get() = channel.type?.stringValue

    fun update(data: ChannelFields): Promise<ChannelJs> {
        return channel.update(
            data.name,
            convertToCustomObject(data.custom), // TODO
            data.description,
            data.status,
            ChannelType.from(data.type)
        ).then {
            it.asJs()
        }.asPromise()
    }

    fun delete(options: DeleteParameters?): Promise<Any> {
        return channel.delete(options?.soft ?: false).then {
            it?.asJs() ?: true
        }.asPromise()
    }

    fun streamUpdates(callback: (channel: ChannelJs?) -> Unit): () -> Unit {
        val closeable = channel.streamUpdates {
            callback(it?.asJs())
        }
        return closeable::close
    }

    fun sendText(text: String, options: dynamic): Promise<Any> {
        val publishOptions = options.unsafeCast<PubNub.PublishParameters?>()
        val files = (options?.files as? Any)?.let { files ->
            val filesArray =
                files as? Array<*> ?: arrayOf(files)
            filesArray.filterNotNull().map { file ->
                InputFile("", file.asDynamic().type ?: file.asDynamic().mimeType ?: "", UploadableImpl(file))
            }
        } ?: listOf()
        return channel.sendText(
            text,
            publishOptions?.meta?.unsafeCast<JsMap<Any>>()?.toMap(),
            publishOptions?.storeInHistory ?: true,
            publishOptions?.sendByPost ?: false,
            publishOptions?.ttl?.toInt(),
            options?.mentionedUsers?.unsafeCast<JsMap<MessageMentionedUser>>()?.toMap()?.mapKeys { it.key.toInt() },
            options?.referencedChannels?.unsafeCast<JsMap<MessageReferencedChannel>>()?.toMap()
                ?.mapKeys { it.key.toInt() },
            options?.textLinks?.unsafeCast<Array<TextLink>>()?.toList(),
            (options?.message as? MessageJs)?.message,
            files
        ).then { result ->
            createJsObject<PubNub.SignalResponse> { timetoken = result.timetoken.toString() }
        }.asPromise()
    }

    fun forwardMessage(message: MessageJs): Promise<PubNub.PublishResponse> {
        return channel.forwardMessage(message.message).then { result ->
            createJsObject<PubNub.PublishResponse> { timetoken = result.timetoken.toString() }
        }.asPromise()
    }

    fun startTyping(): Promise<Any?> { // difference in result type
        return channel.startTyping().then { undefined }.asPromise()
    }

    fun stopTyping(): Promise<Any?> { // difference in result type
        return channel.stopTyping().then { undefined }.asPromise()
    }

    fun getTyping(callback: (Array<String>) -> Unit): () -> Unit {
        return channel.getTyping { callback(it.toTypedArray()) }
            .let { autoCloseable ->
                autoCloseable::close
            }
    }

    fun connect(callback: (MessageJs) -> Unit): () -> Unit {
        return channel.connect {
            callback(it.asJs())
        }::close
    }

    fun whoIsPresent(): Promise<Array<String>> {
        return channel.whoIsPresent().then { it.toTypedArray() }.asPromise()
    }

    fun isPresent(userId: String): Promise<Boolean> {
        return channel.isPresent(userId).asPromise()
    }

    fun streamPresence(callback: (Array<String>) -> Unit): Promise<() -> Unit> {
        return channel.streamPresence { callback(it.toTypedArray()) }.let {
            it::close
        }.asFuture().asPromise()
    }

    open fun getHistory(params: dynamic): Promise<HistoryResponseJs> {
        return channel.getHistory(
            params?.startTimetoken?.toString()?.toLong(),
            params?.endTimetoken?.toString()?.toLong(),
            params?.count?.toString()?.toInt() ?: 25
        ).then { result ->
            createJsObject<HistoryResponseJs> {
                this.isMore = result.isMore
                this.messages = result.messages.map(Message::asJs).toTypedArray()
            }
        }.asPromise()
    }

    fun getMessage(timetoken: String): Promise<MessageJs> {
        return channel.getMessage(timetoken.tryLong()!!).then { it!!.asJs() }.asPromise()
    }

    fun join(callback: (MessageJs) -> Unit, params: PubNub.SetMembershipsParameters?): Promise<Any> {
        return channel.join { callback(it.asJs()) }.then {
            val response = Any().asDynamic()
            response.membership = it.membership.asJs()
            response.disconnect = it.disconnect?.let { autoCloseable -> autoCloseable::close } ?: {}
            response
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
                this.members = result.members.map { it.asJs() }.toTypedArray()
            }
        }.asPromise()
    }

    fun invite(user: UserJs): Promise<MembershipJs> {
        return channel.invite(user.user).then { it.asJs() }.asPromise()
    }

    fun inviteMultiple(users: Array<UserJs>): Promise<Array<MembershipJs>> {
        return channel.inviteMultiple(users.map { it.user })
            .then { memberships ->
                memberships.map { it.asJs() }.toTypedArray()
            }.asPromise()
    }

    open fun pinMessage(message: MessageJs): Promise<ChannelJs> {
        return channel.pinMessage(message.message).then { it.asJs() }.asPromise()
    }

    open fun unpinMessage(): Promise<ChannelJs> {
        return channel.unpinMessage().then { it.asJs() }.asPromise()
    }

    fun getPinnedMessage(): Promise<MessageJs?> {
        return channel.getPinnedMessage().then { it?.asJs() }.asPromise()
    }

    /*getUserSuggestions(text: string, options?: {
        limit: number;
    }): Promise<Membership[]>;*/

    fun createMessageDraft(config: MessageDraftConfig?): MessageDraftJs {
        return MessageDraftJs(
            MessageDraftImpl(
                this.channel,
                config?.userSuggestionSource?.let {
                    userSuggestionSourceFrom(it)
                } ?: MessageDraft.UserSuggestionSource.CHANNEL,
                config?.isTypingIndicatorTriggered ?: (channel.type != ChannelType.PUBLIC),
                config?.userLimit ?: 10,
                config?.channelLimit ?: 10
            ),
            config
        )
    }

    fun registerForPush(): Promise<Any> {
        return channel.registerForPush().asPromise()
    }

    fun unregisterFromPush(): Promise<Any> {
        return channel.unregisterFromPush().asPromise()
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

    fun getFiles(params: PubNub.ListFilesParameters?): Promise<GetFilesResultJs> {
        return channel.getFiles(
            params?.limit?.toInt() ?: 100,
            params?.next
        ).then { result ->
            createJsObject<GetFilesResultJs> {
                this.files = result.files.toTypedArray()
                this.next = result.next
                this.total = result.total
            }
        }.asPromise()
    }

    fun deleteFile(params: dynamic): Promise<PubNub.DeleteFileResponse> {
        return channel.deleteFile(params.id, params.name).then {
            createJsObject<PubNub.DeleteFileResponse> {
                this.status = it.status
            }
        }.asPromise()
    }

    fun setRestrictions(user: UserJs, params: RestrictionJs): Promise<Any> {
        return channel.setRestrictions(
            user.user,
            params.ban ?: false,
            params.mute ?: false,
            params.reason?.toString()
        ).asPromise()
    }

    fun getUserRestrictions(user: UserJs): Promise<RestrictionJs> {
        return channel.getUserRestrictions(user.user).then { it.asJs() }.asPromise()
    }

    fun getUsersRestrictions(params: PubNub.GetChannelMembersParameters?): Promise<GetRestrictionsResponseJs> {
        return channel.getUsersRestrictions(
            params?.limit?.toInt(),
            params?.page?.toKmp(),
            extractSortKeys(params?.sort),
        ).then {
            it.toJs()
        }.asPromise()
    }

    fun toJSON(): Json {
        return json(
            "id" to id,
            "name" to name,
            "custom" to custom,
            "description" to description,
            "updated" to updated,
            "status" to status,
            "type" to type
        )
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(channels: Array<ChannelJs>, callback: (Array<ChannelJs>) -> Unit): () -> Unit {
            val closeable = BaseChannel.streamUpdatesOn(channels.map { jsChannel -> jsChannel.channel }) {
                callback(it.map { kmpChannel -> ChannelJs(kmpChannel) }.toTypedArray())
            }
            return closeable::close
        }
    }
}

internal fun Channel.asJs() = ChannelJs(this)

private fun userSuggestionSourceFrom(lowercaseString: String): MessageDraft.UserSuggestionSource {
    return MessageDraft.UserSuggestionSource.entries.first { it.name.lowercase() == lowercaseString }
}
