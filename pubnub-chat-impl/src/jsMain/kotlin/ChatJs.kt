@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.PubNubImpl
import com.pubnub.api.createJsonElement
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.PUBNUB_CHAT_VERSION
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelMentionData
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateDirectConversationResult
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.ThreadMentionData
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toMap
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("Chat")
class ChatJs internal constructor(val chat: ChatInternal, val config: ChatConfig) {
    val currentUser: UserJs get() = chat.currentUser.asJs(this@ChatJs)

    val sdk: PubNub get() = (chat.pubNub as PubNubImpl).jsPubNub

    fun emitEvent(event: dynamic): Promise<PubNub.PublishResponse> {
        val channel: String = event.channel ?: event.user
        val type = event.type
        val payload = event.payload

        val method = if (event.method == "signal") {
            EmitEventMethod.SIGNAL
        } else {
            EmitEventMethod.PUBLISH
        }

        val eventContent = if (type == "custom") {
            EventContent.Custom((payload as JsMap<Any?>).toMap(), method)
        } else {
            payload.type = type
            PNDataEncoder.decode(createJsonElement(payload))
        }
        return chat.emitEvent(
            channel,
            eventContent
        ).then { it.toPublishResponse() }.asPromise()
    }

    fun listenForEvents(event: ListenForEventsParams): () -> Unit {
        val klass = when (event.type) {
            "typing" -> EventContent.Typing::class
            "report" -> EventContent.Report::class
            "receipt" -> EventContent.Receipt::class
            "mention" -> EventContent.Mention::class
            "invite" -> EventContent.Invite::class
            "custom" -> EventContent.Custom::class
            "moderation" -> EventContent.Moderation::class
            else -> throw IllegalArgumentException("Unknown event type ${event.type}")
        }
        val channel: String = event.channel ?: event.user!!
        val method = if (event.method == "signal") {
            EmitEventMethod.SIGNAL
        } else {
            EmitEventMethod.PUBLISH
        }
        return chat.listenForEvents(
            klass,
            channel,
            method
        ) {
            event.callback(it.toJs(this))
        }::close
    }

    fun getEventsHistory(params: GetEventsHistoryParams): Promise<GetEventsHistoryResultJs> {
        return chat.getEventsHistory(
            params.channel,
            params.startTimetoken?.toLong(),
            params.endTimetoken?.toLong(),
            params.count?.toInt() ?: 100
        ).then { result ->
            createJsObject<GetEventsHistoryResultJs> {
                events = result.events.map { it.toJs(this@ChatJs) }.toTypedArray()
                isMore = result.isMore
            }
        }.asPromise()
    }

    fun getUser(id: String): Promise<UserJs?> {
        return chat.getUser(id).then { it?.asJs(this@ChatJs) }.asPromise()
    }

    fun createUser(id: String, data: UserFields): Promise<UserJs> {
        return chat.createUser(
            id,
            data.name,
            data.externalId,
            data.profileUrl,
            data.email,
            data.custom?.let { convertToCustomObject(data.custom) },
            data.status,
            data.type
        ).then { it.asJs(this@ChatJs) }.asPromise()
    }

    fun updateUser(id: String, data: UserFields): Promise<UserJs> {
        return chat.updateUser(
            id,
            data.name,
            data.externalId,
            data.profileUrl,
            data.email,
            data.custom?.let { convertToCustomObject(data.custom) },
            data.status,
            data.type
        ).then { it.asJs(this@ChatJs) }.asPromise()
    }

    fun deleteUser(id: String, params: DeleteParameters?): Promise<DeleteUserResult> {
        return chat.deleteUser(id, params?.soft ?: false)
            .then {
                if (it != null) {
                    DeleteUserResult(it.asJs(this@ChatJs))
                } else {
                    DeleteUserResult(true)
                }
            }.asPromise()
    }

    fun getUsers(params: PubNub.GetAllMetadataParameters?): Promise<GetUsersResponseJs> {
        return chat.getUsers(
            params?.filter,
            extractSortKeys(params?.sort),
            params?.limit?.toInt(),
            params?.page?.toKmp()
        ).then { result ->
            createJsObject<GetUsersResponseJs> {
                this.users = result.users.map { it.asJs(this@ChatJs) }.toTypedArray()
                this.page = MetadataPage(result.next, result.prev)
                this.total = result.total
            }
        }.asPromise()
    }

    fun getChannel(id: String): Promise<ChannelJs?> {
        return chat.getChannel(id).then { it?.asJs(this) }.asPromise()
    }

    fun updateChannel(id: String, data: ChannelFields): Promise<ChannelJs> {
        return chat.updateChannel(
            id,
            data.name,
            data.custom?.let { convertToCustomObject(it) },
            data.description,
            data.status,
            data.type?.let { ChannelType.from(data.type) }
        ).then { it.asJs(this@ChatJs) }.asPromise()
    }

    fun getChannels(params: PubNub.GetAllMetadataParameters?): Promise<GetChannelsResponseJs> {
        return chat.getChannels(
            params?.filter,
            extractSortKeys(params?.sort),
            params?.limit?.toInt(),
            params?.page?.toKmp()
        ).then { result ->
            createJsObject<GetChannelsResponseJs> {
                this.channels = result.channels.map { it.asJs(this@ChatJs) }.toTypedArray()
                this.page = MetadataPage(result.next, result.prev)
                this.total = result.total
            }
        }.asPromise()
    }

    fun deleteChannel(id: String, params: DeleteParameters?): Promise<DeleteChannelResult> {
        return chat.deleteChannel(id, params?.soft ?: false)
            .then {
                if (it != null) {
                    DeleteChannelResult(it.asJs(this@ChatJs))
                } else {
                    DeleteChannelResult(true)
                }
            }.asPromise()
    }

    // internal
    fun createChannel(id: String, data: PubNub.ChannelMetadata?): Promise<ChannelJs> {
        return chat.createChannel(
            id,
            data?.name,
            data?.description,
            convertToCustomObject(data?.custom),
            ChannelType.from(data?.type),
            data?.status
        ).then { it.asJs(this@ChatJs) }.asPromise()
    }

    // internal
    fun getThreadId(channelId: String, messageId: String) =
        ChatImpl.getThreadId(channelId, messageId.toLong())

    fun createPublicConversation(params: CreateChannelParams?): Promise<ChannelJs> {
        val channelId: String? = params?.channelId
        val data: PubNub.ChannelMetadata? = params?.channelData
        return chat.createPublicConversation(
            channelId,
            data?.name,
            data?.description,
            convertToCustomObject(data?.custom),
            data?.status
        ).then { it.asJs(this@ChatJs) }.asPromise()
    }

    fun createDirectConversation(params: CreateDirectConversationParams): Promise<CreateDirectConversationResultJs> {
        val user: UserJs = params.user
        val channelId: String? = params.channelId
        val data: PubNub.ChannelMetadata? = params.channelData
        val membershipCustom: Any? = params.membershipData?.custom
        return chat.createDirectConversation(
            user.user,
            channelId,
            data?.name,
            data?.description,
            convertToCustomObject(data?.custom),
            data?.status,
            convertToCustomObject(membershipCustom),
        ).then { result ->
            result.toJs()
        }.asPromise()
    }

    fun createGroupConversation(params: CreateGroupConversationParams): Promise<CreateGroupConversationResultJs> {
        val users: Array<UserJs> = params.users
        val channelId: String? = params.channelId
        val data: PubNub.ChannelMetadata? = params.channelData
        val membershipCustom: Any? = params.membershipData?.custom
        return chat.createGroupConversation(
            users.map { it.user },
            channelId,
            data?.name,
            data?.description,
            convertToCustomObject(data?.custom),
            data?.status,
            convertToCustomObject(membershipCustom),
        ).then { result ->
            result.toJs()
        }.asPromise()
    }

    fun wherePresent(id: String): Promise<Array<String>> {
        return chat.wherePresent(id).then { it.toTypedArray() }.asPromise()
    }

    fun whoIsPresent(id: String): Promise<Array<String>> {
        return chat.whoIsPresent(id).then { it.toTypedArray() }.asPromise()
    }

    fun isPresent(userId: String, channelId: String): Promise<Boolean> {
        return chat.isPresent(userId, channelId).asPromise()
    }

    fun registerPushChannels(channels: Array<String>): Promise<Any> {
        return chat.registerPushChannels(channels.toList()).asPromise()
    }

    fun unregisterPushChannels(channels: Array<String>): Promise<Any> {
        return chat.unregisterPushChannels(channels.toList()).asPromise()
    }

    fun unregisterAllPushChannels(): Promise<Any> {
        return chat.unregisterAllPushChannels().asPromise()
    }

    fun getPushChannels(): Promise<Array<String>> {
        return chat.getPushChannels().then { it.toTypedArray() }.asPromise()
    }

    fun getCurrentUserMentions(
        params: GetHistoryParams?
    ): Promise<GetCurrentUserMentionsResultJs> {
        return chat.getCurrentUserMentions(
            params?.startTimetoken?.toLong(),
            params?.endTimetoken?.toLong(),
            params?.count?.toInt() ?: 100
        ).then { result ->
            createJsObject<GetCurrentUserMentionsResultJs> {
                this.isMore = result.isMore
                this.enhancedMentionsData = result.enhancedMentionsData.map {
                    when (it) {
                        is ChannelMentionData -> createJsObject<ChannelMentionDataJs> {
                            this.userId = it.userId
                            this.channelId = it.channelId
                            this.event = it.event.toJs(this@ChatJs)
                            this.message = it.message.asJs(this@ChatJs)
                        }

                        is ThreadMentionData -> createJsObject<ThreadMentionDataJs> {
                            this.userId = it.userId
                            this.parentChannelId = it.parentChannelId
                            this.threadChannelId = it.threadChannelId
                            this.event = it.event.toJs(this@ChatJs)
                            this.message = it.message.asJs(this@ChatJs)
                        }
                    }
                }.toTypedArray()
            }
        }.asPromise()
    }

    fun getUnreadMessagesCounts(params: PubNub.GetMembershipsParametersv2?): Promise<Array<GetUnreadMessagesCountsJs>> {
        return chat.getUnreadMessagesCounts(
            params?.limit?.toInt(),
            params?.page?.toKmp(),
            params?.filter,
            extractSortKeys(params?.sort)
        ).then { result ->
            result.map { unreadMessagesCount ->
                createJsObject<GetUnreadMessagesCountsJs> {
                    this.channel = unreadMessagesCount.channel.asJs(this@ChatJs)
                    this.membership = unreadMessagesCount.membership.asJs(this@ChatJs)
                    this.count = unreadMessagesCount.count.toDouble()
                }
            }.toTypedArray()
        }.asPromise()
    }

    fun markAllMessagesAsRead(params: PubNub.GetMembershipsParametersv2?): Promise<MarkAllMessageAsReadResponseJs> {
        return chat.markAllMessagesAsRead(
            params?.limit?.toInt(),
            params?.page?.toKmp(),
            params?.filter,
            extractSortKeys(params?.sort)
        ).then { result ->
            createJsObject<MarkAllMessageAsReadResponseJs> {
                this.page = MetadataPage(result.next, result.prev)
                this.total = result.total
                this.status = result.status
                this.memberships = result.memberships.map { it.asJs(this@ChatJs) }.toTypedArray()
            }
        }.asPromise()
    }

    fun setRestrictions(userId: String, channelId: String, params: RestrictionJs): Promise<Any> {
        return chat.setRestrictions(
            Restriction(
                userId,
                channelId,
                params.ban ?: false,
                params.mute ?: false,
                params.reason?.toString()
            )
        ).asPromise()
    }

    private fun CreateDirectConversationResult.toJs() =
        createJsObject<CreateDirectConversationResultJs> {
            this.channel = this@toJs.channel.asJs(this@ChatJs)
            this.hostMembership = this@toJs.hostMembership.asJs(this@ChatJs)
            this.inviteeMembership = this@toJs.inviteeMembership.asJs(this@ChatJs)
        }

    private fun CreateGroupConversationResult.toJs() =
        createJsObject<CreateGroupConversationResultJs> {
            this.channel = this@toJs.channel.asJs(this@ChatJs)
            this.hostMembership = this@toJs.hostMembership.asJs(this@ChatJs)
            this.inviteesMemberships = this@toJs.inviteeMemberships.map { it.asJs(this@ChatJs) }.toTypedArray()
        }

    fun toJSON(): Json {
        return json("config" to config, "currentUser" to currentUser)
    }

    @Deprecated("Only for internal MessageDraft V1 use")
    fun getUserSuggestions(text: String, options: GetSuggestionsParams?): Promise<Array<UserJs>> {
        val limit = options?.limit
        val cacheKey = MessageElementsUtils.getPhraseToLookFor(text) ?: return Promise.resolve(emptyArray<UserJs>())
        return chat.getUserSuggestions(cacheKey, limit?.toInt() ?: 10).then { users ->
            users.map { it.asJs(this@ChatJs) }.toTypedArray()
        }.asPromise()
    }

    @Deprecated("Only for internal MessageDraft V1 use")
    fun getChannelSuggestions(text: String, options: GetSuggestionsParams?): Promise<Array<ChannelJs>> {
        val limit = options?.limit
        val cacheKey = MessageElementsUtils.getChannelPhraseToLookFor(text) ?: return Promise.resolve(emptyArray<ChannelJs>())
        return chat.getChannelSuggestions(cacheKey, limit?.toInt() ?: 10).then { channels ->
            channels.map { it.asJs(this@ChatJs) }.toTypedArray()
        }.asPromise()
    }

    companion object {
        @JsStatic
        fun init(config: ChatConstructor): Promise<ChatJs> {
            val pubnub = PubNub(config)
            pubnub.asDynamic()._config._addPnsdkSuffix("chat-sdk", "CA-TS/$PUBNUB_CHAT_VERSION")
            return ChatImpl(config.toChatConfiguration(), PubNubImpl(pubnub)).initialize().then {
                ChatJs(it as ChatInternal, config)
            }.asPromise()
        }
    }
}
