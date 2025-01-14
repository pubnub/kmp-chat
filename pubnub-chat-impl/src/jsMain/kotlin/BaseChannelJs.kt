@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.BaseChannel
import com.pubnub.chat.Channel
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.InputFile
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
@JsName("BaseChannel")
open class BaseChannelJs internal constructor(internal val baseChannel: BaseChannel<*, *>, internal val chatJs: ChatJs) : ChannelFields {
    override val id: String by baseChannel::id
    override val name: String? by baseChannel::name
    override val custom: Any? get() = baseChannel.custom?.toJsMap()
    override val description: String? by baseChannel::description
    override val updated: String? by baseChannel::updated
    override val status: String? by baseChannel::status
    override val type: String? get() = baseChannel.type?.stringValue

    fun update(data: ChannelFields): Promise<BaseChannelJs> {
        return baseChannel.update(
            data.name,
            data.custom?.let { convertToCustomObject(it) },
            data.description,
            data.status,
            data.type?.let { ChannelType.from(it) }
        ).then {
            it.asJs(chatJs)
        }.asPromise()
    }

    fun delete(options: DeleteParameters?): Promise<DeleteChannelResult> {
        return baseChannel.delete(options?.soft ?: false).then {
            if (it != null) {
                DeleteChannelResult(it.asJs(chatJs))
            } else {
                DeleteChannelResult(true)
            }
        }.asPromise()
    }

    fun streamUpdates(callback: (channel: BaseChannelJs?) -> Unit): () -> Unit {
        val closeable = baseChannel.streamUpdates {
            callback(it?.asJs(chatJs))
        }
        return closeable::close
    }

    fun sendText(text: String, options: SendTextOptionParams?): Promise<PubNub.PublishResponse> {
        @Suppress("USELESS_CAST") // cast required to be able to call "let" extension function
        val files = (options?.files as? Any)?.let { files ->
            val filesArray =
                files as? Array<*> ?: arrayOf(files)
            filesArray.filterNotNull().map { file ->
                InputFile("", file.asDynamic().type ?: file.asDynamic().mimeType ?: "", UploadableImpl(file))
            }
        } ?: listOf()
        return baseChannel.sendText(
            text = text,
            meta = options?.meta?.unsafeCast<JsMap<Any>>()?.toMap(),
            shouldStore = options?.storeInHistory ?: true,
            usePost = options?.sendByPost ?: false,
            ttl = options?.ttl?.toInt(),
            mentionedUsers = options?.mentionedUsers?.toMap()?.mapKeys { it.key.toInt() },
            referencedChannels = options?.referencedChannels?.toMap()?.mapKeys { it.key.toInt() },
            textLinks = options?.textLinks?.toList(),
            quotedMessage = options?.quotedMessage?.baseMessage,
            files = files,
            customPushData = options?.customPushData?.toMap()
        ).then { result ->
            result.toPublishResponse()
        }.asPromise()
    }

    fun forwardMessage(message: BaseMessageJs): Promise<PubNub.PublishResponse> {
        return baseChannel.forwardMessage(message.baseMessage).then { it.toPublishResponse() }.asPromise()
    }

    fun startTyping(): Promise<PubNub.PublishResponse?> {
        return baseChannel.startTyping().then { it?.toPublishResponse() ?: undefined }.asPromise()
    }

    fun stopTyping(): Promise<PubNub.PublishResponse?> {
        return baseChannel.stopTyping().then { it?.toPublishResponse() ?: undefined }.asPromise()
    }

    fun getTyping(callback: (Array<String>) -> Unit): () -> Unit {
        return baseChannel.getTyping { callback(it.toTypedArray()) }
            .let { autoCloseable ->
                autoCloseable::close
            }
    }

    fun connect(callback: (BaseMessageJs) -> Unit): () -> Unit {
        return baseChannel.connect {
            callback(it.asJs(chatJs))
        }::close
    }

    fun whoIsPresent(): Promise<Array<String>> {
        return baseChannel.whoIsPresent().then { it.toTypedArray() }.asPromise()
    }

    fun isPresent(userId: String): Promise<Boolean> {
        return baseChannel.isPresent(userId).asPromise()
    }

    fun streamPresence(callback: (Array<String>) -> Unit): Promise<() -> Unit> {
        return baseChannel.streamPresence { callback(it.toTypedArray()) }.let {
            it::close
        }.asFuture().asPromise()
    }

    open fun getHistory(params: GetHistoryParams?): Promise<HistoryResponseJs> {
        return baseChannel.getHistory(
            params?.startTimetoken?.toLong(),
            params?.endTimetoken?.toLong(),
            params?.count?.toInt() ?: 25
        ).then { result ->
            createJsObject<HistoryResponseJs> {
                this.isMore = result.isMore
                this.messages = result.messages.map { it.asJs(chatJs) }.toTypedArray()
            }
        }.asPromise()
    }

    fun getMessage(timetoken: String): Promise<BaseMessageJs> {
        return baseChannel.getMessage(timetoken.tryLong()!!).then { it!!.asJs(chatJs) }.asPromise()
    }

    open fun pinMessage(message: BaseMessageJs): Promise<BaseChannelJs> {
        return baseChannel.pinMessage(message.baseMessage).then { it.asJs(chatJs) }.asPromise()
    }

    open fun unpinMessage(): Promise<BaseChannelJs> {
        return baseChannel.unpinMessage().then { it.asJs(chatJs) }.asPromise()
    }

    fun getPinnedMessage(): Promise<BaseMessageJs?> {
        return baseChannel.getPinnedMessage().then { it?.asJs(chatJs) }.asPromise()
    }

    fun createMessageDraft(config: MessageDraftConfig?): MessageDraftV1Js {
        return MessageDraftV1Js(
            chatJs,
            this,
            config
        )
    }

    fun createMessageDraftV2(config: MessageDraftConfig?): MessageDraftV2Js {
        return MessageDraftV2Js(
            this.chatJs,
            MessageDraftImpl(
                this.baseChannel,
                config?.userSuggestionSource?.let {
                    userSuggestionSourceFrom(it)
                } ?: MessageDraft.UserSuggestionSource.CHANNEL,
                config?.isTypingIndicatorTriggered ?: (baseChannel.type != ChannelType.PUBLIC),
                config?.userLimit ?: 10,
                config?.channelLimit ?: 10
            ),
            createJsObject<MessageDraftConfig> {
                this.userSuggestionSource = config?.userSuggestionSource ?: "channel"
                this.isTypingIndicatorTriggered = config?.isTypingIndicatorTriggered ?: (baseChannel.type != ChannelType.PUBLIC)
                this.userLimit = config?.userLimit ?: 10
                this.channelLimit = config?.channelLimit ?: 10
            }
        )
    }

    fun registerForPush(): Promise<Any> {
        return baseChannel.registerForPush().asPromise()
    }

    fun unregisterFromPush(): Promise<Any> {
        return baseChannel.unregisterFromPush().asPromise()
    }

    fun getFiles(params: PubNub.ListFilesParameters?): Promise<GetFilesResultJs> {
        return baseChannel.getFiles(
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

    fun deleteFile(params: DeleteFileParams): Promise<PubNub.DeleteFileResponse> {
        return baseChannel.deleteFile(params.id, params.name).then {
            createJsObject<PubNub.DeleteFileResponse> {
                this.status = it.status
            }
        }.asPromise()
    }

    fun setRestrictions(user: UserJs, params: RestrictionJs): Promise<Any> {
        return baseChannel.setRestrictions(
            user.user,
            params.ban ?: false,
            params.mute ?: false,
            params.reason?.toString()
        ).asPromise()
    }

    fun getUserRestrictions(user: UserJs): Promise<RestrictionJs> {
        return baseChannel.getUserRestrictions(user.user).then { it.asJs() }.asPromise()
    }

    fun getUsersRestrictions(params: PubNub.GetChannelMembersParameters?): Promise<GetRestrictionsResponseJs> {
        return baseChannel.getUsersRestrictions(
            params?.limit?.toInt(),
            params?.page?.toKmp(),
            extractSortKeys(params?.sort),
        ).then {
            it.toJs()
        }.asPromise()
    }

    fun streamMessageReports(callback: (EventJs) -> Unit): () -> Unit =
        baseChannel.streamMessageReports { event ->
            callback(event.toJs(chatJs))
        }::close

    @Deprecated("Only for internal MessageDraft V1 use")
    fun getUserSuggestions(text: String, options: GetSuggestionsParams?): Promise<Array<MembershipJs>> {
        val limit = options?.limit
        val cacheKey = MessageElementsUtils.getPhraseToLookFor(text) ?: return Promise.resolve(emptyArray<MembershipJs>())
        return baseChannel.getUserSuggestions(cacheKey, limit?.toInt() ?: 10).then { memberships ->
            memberships.map { it.asJs(chatJs) }.toTypedArray()
        }.asPromise()
    }

    fun getMessageReportsHistory(params: GetHistoryParams?): Promise<GetMessageReportsHistoryResult> {
        return this.baseChannel.getMessageReportsHistory(
            params?.startTimetoken?.toLong(),
            params?.endTimetoken?.toLong(),
            params?.count ?: 100
        ).then { result ->
            createJsObject<GetMessageReportsHistoryResult> {
                events = result.events.map { it.toJs(chatJs) }.toTypedArray()
                isMore = result.isMore
            }
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
}

internal fun BaseChannel<*, *>.asJs(chat: ChatJs): BaseChannelJs = when (this) {
    is Channel -> ChannelJs(this, chat)
    is ThreadChannel -> ThreadChannelJs(this, chat)
    else -> error("Unexpected error. $this is not a `Channel` or `ThreadChannel`")
}

private fun userSuggestionSourceFrom(lowercaseString: String): MessageDraft.UserSuggestionSource {
    return MessageDraft.UserSuggestionSource.entries.first { it.name.lowercase() == lowercaseString }
}
