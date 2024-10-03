@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.PubNubImpl
import com.pubnub.api.createJsonElement
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.SortField
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.RateLimitPerChannel
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelMentionData
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateDirectConversationResult
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetFileItem
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink
import com.pubnub.chat.types.ThreadMentionData
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.UploadableImpl
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import com.pubnub.kmp.toMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.js.Promise
import kotlin.time.Duration.Companion.milliseconds

external interface GetEventsHistoryParams {
    val channel: String
    val startTimetoken: String?
    val endTimetoken: String?
    val count: Number?
}

@JsExport
@JsName("Chat")
class ChatJs(val config: ChatConfig) {
    private lateinit var chat: Chat
    val currentUser: UserJs get() = chat.currentUser.asJs()

    val sdk: PubNub get() = (chat.pubNub as PubNubImpl).jsPubNub

    @JsExport.Ignore
    internal constructor(chat: Chat, config: ChatConfig) : this(config) {
        this.chat = chat
    }

    fun emitEvent(event: dynamic): Promise<PubNub.SignalResponse> {
        val channel: String = event.channel ?: event.user
        val type = event.type
        val payload = event.payload
        payload.type = type
        return chat.emitEvent(
            channel,
            PNDataEncoder.decode(createJsonElement(payload))
        ).then { result ->
            createJsObject<PubNub.SignalResponse> { timetoken = result.timetoken.toDouble() }
        }.asPromise()
    }

    fun listenForEvents(event: dynamic): () -> Unit {
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
        val channel: String = event.channel ?: event.user
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
        }.let { closeable ->
            closeable::close
        }
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
        return chat.getUser(id).then { it?.asJs() }.asPromise()
    }

    fun createUser(id: String, data: UserFields): Promise<UserJs> {
        return chat.createUser(
            id,
            data.name,
            data.externalId,
            data.profileUrl,
            data.email,
            createCustomObject(data.custom.unsafeCast<JsMap<Any?>>().toMap()),
            data.status,
            data.type
        ).then { it.asJs() }.asPromise()
    }

    fun updateUser(id: String, data: UserFields): Promise<UserJs> {
        return chat.updateUser(
            id,
            data.name,
            data.externalId,
            data.profileUrl,
            data.email,
            createCustomObject(data.custom.unsafeCast<JsMap<Any?>>().toMap()),
            data.status,
            data.type
        ).then { it.asJs() }.asPromise()
    }

    fun deleteUser(id: String, params: DeleteParameters?): Promise<UserJs> {
        return chat.deleteUser(id, params?.soft ?: false).then { it.asJs() }.asPromise()
    }

    fun getUsers(params: PubNub.GetAllMetadataParameters?): Promise<GetUsersResponseJs> {
        return chat.getUsers(
            params?.filter,
            extractSortKeys(params),
            params?.limit?.toInt(),
            params?.page?.toKmp()
        ).then { result ->
            createJsObject<GetUsersResponseJs> {
                this.users = result.users.map { it.asJs() }.toTypedArray()
                this.page = MetadataPage(result.next, result.prev)
                this.total = result.total
            }
        }.asPromise()
    }

    fun getChannel(id: String): Promise<ChannelJs?> {
        return chat.getChannel(id).then { it?.asJs() }.asPromise()
    }

    fun updateChannel(id: String, data: ChannelFields): Promise<ChannelJs> {
        return chat.updateChannel(
            id,
            data.name,
            createCustomObject(data.custom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
            data.description,
            data.status,
            ChannelType.from(data.type)
        ).then { it.asJs() }.asPromise()
    }

    fun getChannels(params: PubNub.GetAllMetadataParameters?): Promise<GetChannelsResponseJs> {
        return chat.getChannels(
            params?.filter,
            extractSortKeys(params),
            params?.limit?.toInt(),
            params?.page?.toKmp()
        ).then { result ->
            createJsObject<GetChannelsResponseJs> {
                this.users = result.channels.map { it.asJs() }.toTypedArray()
                this.page = MetadataPage(result.next, result.prev)
                this.total = result.total
            }
        }.asPromise()
    }

    fun deleteChannel(id: String, params: DeleteParameters?): Promise<ChannelJs> {
        return chat.deleteChannel(id, params?.soft ?: false).then { it.asJs() }.asPromise()
    }

    fun createPublicConversation(params: dynamic): Promise<ChannelJs> {
        val channelId: String? = params.channelId
        val data: PubNub.ChannelMetadata? = params.channelData
        return chat.createPublicConversation(
            channelId,
            data?.name,
            data?.description,
            createCustomObject(data?.custom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
            data?.status
        ).then { it.asJs() }.asPromise()
    }

    fun createDirectConversation(params: dynamic): Promise<CreateDirectConversationResultJs> {
        val user: UserJs = params.user
        val channelId: String? = params.channelId
        val data: PubNub.ChannelMetadata? = params.channelData
        val membershipCustom: Any? = params.membershipData?.custom
        return chat.createDirectConversation(
            user.user,
            channelId,
            data?.name,
            data?.description,
            createCustomObject(data?.custom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
            data?.status,
            createCustomObject(membershipCustom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
        ).then { result ->
            result.toJs()
        }.asPromise()
    }

    fun createGroupConversation(params: dynamic): Promise<CreateGroupConversationResultJs> {
        val users: Array<UserJs> = params.users
        val channelId: String? = params.channelId
        val data: PubNub.ChannelMetadata? = params.channelData
        val membershipCustom: Any? = params.membershipData?.custom
        return chat.createGroupConversation(
            users.map { it.user },
            channelId,
            data?.name,
            data?.description,
            createCustomObject(data?.custom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
            data?.status,
            createCustomObject(membershipCustom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
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

    /* TODO getUserSuggestions(text: string, options?: {
        limit: number;
    }): Promise<User[]>;
    getChannelSuggestions(text: string, options?: {
        limit: number;
    }): Promise<Channel[]>; */

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
        params: dynamic/*params?: {
        startTimetoken?: string;
        endTimetoken?: string;
        count?: number;
    }*/
    ): Promise<GetCurrentUserMentionsResultJs> {
        val startTimetoken: String? = params.startTimetoken
        val endTimetoken: String? = params.endTimetoken
        val count: Number? = params.count
        return chat.getCurrentUserMentions(
            startTimetoken?.toLong(),
            endTimetoken?.toLong(),
            count?.toInt() ?: 100
        ).then { result ->
            createJsObject<GetCurrentUserMentionsResultJs> {
                this.isMore = result.isMore
                this.enhancedMentionsData = result.enhancedMentionsData.map {
                    when (it) {
                        is ChannelMentionData -> createJsObject<ChannelMentionDataJs> {
                            this.userId = it.userId
                            this.channelId = it.channelId
                            this.event = it.event.toJs(this@ChatJs)
                            this.message = it.message.asJs()
                        }
                        is ThreadMentionData -> createJsObject<ThreadMentionDataJs> {
                            this.userId = it.userId
                            this.parentChannelId = it.parentChannelId
                            this.threadChannelId = it.threadChannelId
                            this.event = it.event.toJs(this@ChatJs)
                            this.message = it.message.asJs()
                        }
                    }
                }.toTypedArray()
            }
        }.asPromise()
    }

    fun getUnreadMessagesCounts(params: PubNub.GetMembershipsParametersv2): Promise<Array<GetUnreadMessagesCountsJs>> {
        return chat.getUnreadMessagesCounts(
            params.limit?.toInt(),
            params.page?.toKmp(),
            params.filter,
            extractSortKeys(params.sort)
        ).then { result: Set<GetUnreadMessagesCounts> ->
            result.map { unreadMessagesCount ->
                createJsObject<GetUnreadMessagesCountsJs> {
                    this.channel = unreadMessagesCount.channel.asJs()
                    this.membership = unreadMessagesCount.membership.asJs()
                    this.count = unreadMessagesCount.count.toDouble()
                }
            }.toTypedArray()
        }.asPromise()
    }

    fun markAllMessagesAsRead(params: PubNub.GetMembershipsParametersv2): Promise<MarkAllMessageAsReadResponseJs> {
        return chat.markAllMessagesAsRead(
            params.limit?.toInt(),
            params.page?.toKmp(),
            params.filter,
            extractSortKeys(params.sort)
        ).then { result ->
            createJsObject<MarkAllMessageAsReadResponseJs> {
                this.page = MetadataPage(result.next, result.prev)
                this.total = result.total
                this.status = result.status
                this.memberships = result.memberships.map { it.asJs() }.toTypedArray()
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
            this.channel = this@toJs.channel.asJs()
            this.hostMembership = this@toJs.hostMembership.asJs()
            this.inviteeMembership = this@toJs.inviteeMembership.asJs()
        }

    private fun CreateGroupConversationResult.toJs() =
        createJsObject<CreateGroupConversationResultJs> {
            this.channel = this@toJs.channel.asJs()
            this.hostMembership = this@toJs.hostMembership.asJs()
            this.inviteesMemberships = this@toJs.inviteeMemberships.map { it.asJs() }.toTypedArray()
        }

    companion object {
        @JsStatic
        fun init(config: ChatConstructor): Promise<ChatJs> {
            return ChatImpl(config.toChatConfiguration(), PubNubImpl(PubNub(config.asDynamic()))).initialize().then {
                ChatJs(it, config)
            }.asPromise()
        }
    }
}

private fun GetRestrictionsResponse.toJs() =
    createJsObject<GetRestrictionsResponseJs> {
        this.page = MetadataPage(next, prev)
        this.restrictions = this@toJs.restrictions.map { it.asJs() }.toTypedArray()
        this.status = this@toJs.status
        this.total = this@toJs.total
    }

private inline fun <reified T : SortField> extractSortKeys(sort: Any?): List<PNSortKey<T>> =
    sort?.unsafeCast<JsMap<String>>()?.toMap()?.map {
        val fieldName = it.key
        val direction = it.value
        when (T::class) {
            PNMembershipKey::class -> getAscOrDesc(direction, PNMembershipKey.valueOf(fieldName))
            PNKey::class -> getAscOrDesc(direction, PNKey.valueOf(fieldName))
            PNMemberKey::class -> getAscOrDesc(direction, PNMemberKey.valueOf(fieldName))
            else -> error("Should never happen")
        } as PNSortKey<T>
    } ?: listOf()

private fun <T : SortField> getAscOrDesc(direction: String, field: T): PNSortKey<T> {
    return if (direction == "asc") {
        PNSortKey.PNAsc(field)
    } else {
        PNSortKey.PNDesc(field)
    }
}

@JsExport
@JsName("MarkAllMessageAsReadResponse")
interface MarkAllMessageAsReadResponseJs {
    var memberships: Array<MembershipJs>
    var page: PubNub.MetadataPage
    var total: Int?
    var status: Int
}

@JsExport
@JsName("GetCurrentUserMentionsResult")
interface GetCurrentUserMentionsResultJs {
    var enhancedMentionsData: Array<UserMentionDataJs>
    var isMore: Boolean
}

@JsExport
@JsName("UserMentionData")
interface UserMentionDataJs {
    val event: EventJs
    val userId: String
    val message: MessageJs
}

@JsExport
@JsName("ChannelMentionData")
interface ChannelMentionDataJs : UserMentionDataJs {
    override var event: EventJs
    override var userId: String
    override var message: MessageJs
    var channelId: String
}

@JsExport
@JsName("ThreadMentionData")
interface ThreadMentionDataJs : UserMentionDataJs {
    override var event: EventJs
    override var userId: String
    override var message: MessageJs
    var parentChannelId: String
    var threadChannelId: String
}

@JsExport
@JsName("GetUnreadMessagesCounts")
interface GetUnreadMessagesCountsJs {
    var channel: ChannelJs
    var membership: MembershipJs
    var count: Double
}

@JsExport
class MessageJs() {
    internal lateinit var message: Message

    internal constructor(message: Message) : this() {
        this.message = message
    }
}

@JsExport
@JsName("CreateGroupConversationResult")
interface CreateGroupConversationResultJs {
    var channel: ChannelJs
    var hostMembership: MembershipJs
    var inviteesMemberships: Array<MembershipJs>
}

@JsExport
@JsName("CreateDirectConversationResult")
interface CreateDirectConversationResultJs {
    var channel: ChannelJs
    var hostMembership: MembershipJs
    var inviteeMembership: MembershipJs
}

@JsExport
@JsName("GetChannelsResponse")
interface GetChannelsResponseJs {
    var users: Array<ChannelJs>
    var page: PubNub.MetadataPage
    var total: Int
}

@JsExport
external interface ChannelFields {
    val id: String
    val name: String?
    val custom: Any?
    val description: String?
    val updated: String?
    val status: String?
    val type: String?
}

@JsExport
@JsName("GetUsersResponse")
external interface GetUsersResponseJs {
    var users: Array<UserJs>
    var page: PubNub.MetadataPage
    var total: Int
}

@JsExport
@JsName("GetEventsHistoryResult")
external interface GetEventsHistoryResultJs {
    var events: Array<EventJs>
    var isMore: Boolean
}

@JsExport
@JsName("Event")
class EventJs(
    val chat: ChatJs,
    val timetoken: String,
    val type: String,
    val payload: dynamic,
    val channelId: String,
    val userId: String
)

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
internal fun Event<*>.toJs(chatJs: ChatJs): EventJs {
    return EventJs(
        chatJs,
        timetoken.toString(),
        payload::class.serializer().descriptor.serialName,
        (PNDataEncoder.encode(payload) as Map<String, Any?>).toJsMap(),
        channelId,
        userId
    )
}

private fun ChatConfig.toChatConfiguration(): ChatConfiguration {
    return ChatConfiguration(
        typingTimeout = typingTimeout.milliseconds,
        storeUserActivityInterval = storeUserActivityInterval.milliseconds,
        storeUserActivityTimestamps = storeUserActivityTimestamps,
        pushNotifications = TODO(),
        rateLimitFactor = rateLimitFactor,
        rateLimitPerChannel = RateLimitPerChannel(),
        customPayloads = TODO(),
    )
}

@JsExport
external interface PushNotificationsConfig {
    val sendPushes: Boolean
    val deviceToken: String?
    val deviceGateway: String? // "apns2" | "gcm"
    val apnsTopic: String?
    val apnsEnvironment: String
}

@JsExport
class ChannelJs() : ChannelFields {
    internal lateinit var channel: Channel

    internal constructor(channel: Channel) : this() {
        this.channel = channel
    }

    override val id: String get() = channel.id
    override val name: String? get() = channel.name
    override val custom: Any? get() = channel.custom // TODO
    override val description: String? get() = channel.description
    override val updated: String? get() = channel.updated
    override val status: String? get() = channel.status
    override val type: String? get() = channel.type?.stringValue

    fun update(data: ChannelFields): Promise<ChannelJs> {
        return channel.update(
            data.name,
            createCustomObject(data.custom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
            data.description,
            data.status,
            ChannelType.from(data.type)
        ).then {
            it.asJs()
        }.asPromise()
    }

    fun delete(options: DeleteParameters?): Promise<ChannelJs> {
        return channel.delete(options?.soft ?: false).then { it.asJs() }.asPromise()
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
                if (files is Array<*>) {
                    files
                } else {
                    arrayOf(files)
                }
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
            options?.referencedChannels?.unsafeCast<JsMap<MessageReferencedChannel>>()?.toMap()?.mapKeys { it.key.toInt() },
            options?.textLinks.unsafeCast<Array<TextLink>>().toList(),
            (options?.message as? MessageJs)?.message,
            files
        ).then { result ->
            createJsObject<PubNub.SignalResponse> { timetoken = result.timetoken.toDouble() }
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
        return channel.connect { callback(it.asJs()) }.let { autoCloseable ->
            autoCloseable::close
        }
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

    fun getHistory(params: dynamic): Promise<HistoryResponseJs> {
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
        return channel.getMessage(timetoken.toLong()).then { it!!.asJs() }.asPromise()
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

    fun pinMessage(message: MessageJs): Promise<ChannelJs> {
        return channel.pinMessage(message.message).then { it.asJs() }.asPromise()
    }

    fun unpinMessage(): Promise<ChannelJs> {
        return channel.unpinMessage().then { it.asJs() }.asPromise()
    }

    fun getPinnedMessage(): Promise<MessageJs?> {
        return channel.getPinnedMessage().then { it?.asJs() }.asPromise()
    }

    /*getUserSuggestions(text: string, options?: {
        limit: number;
    }): Promise<Membership[]>;*/

    /*fun createMessageDraft(config: dynamic): MessageDraft {
        return MessageDraftImpl(channel)
    }*/

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

@JsExport
@JsName("GetFilesResult")
interface GetFilesResultJs {
    var files: Array<GetFileItem>
    var next: String?
    var total: Int
}

@JsExport
@JsName("MembersResponse")
interface MembersResponseJs {
    var page: PubNub.MetadataPage
    var total: Int?
    var status: Int
    var members: Array<MembershipJs>
}

@JsExport
@JsName("HistoryResponse")
interface HistoryResponseJs {
    var messages: Array<MessageJs>
    var isMore: Boolean
}

@JsExport
@JsName("User")
class UserJs private constructor() : UserFields {
    internal lateinit var user: User
    val active: Boolean get() = user.active

    override val id get() = user.id
    override val name get() = user.name
    override val externalId get() = user.externalId
    override val profileUrl get() = user.profileUrl
    override val email get() = user.email
    override val custom: Any? get() = user.custom?.toJsMap()?.asDynamic() // TODO need to convert map values recursively?
    override val status get() = user.status
    override val type get() = user.type
    val updated get() = user.updated
    val lastActiveTimestamp get() = user.lastActiveTimestamp?.toInt()

    internal constructor(user: User) : this() {
        this.user = user
    }

    fun update(data: UserFields): Promise<UserJs> {
        return user.update(
            data.name,
            data.externalId,
            data.profileUrl,
            data.email,
            createCustomObject(data.custom.unsafeCast<JsMap<Any?>>().toMap()), // TODO
            data.status,
            data.type
        ).then {
            UserJs(it)
        }.asPromise()
    }

    fun delete(options: DeleteParameters): Promise</*true | User*/UserJs> { // TODO
        return user.delete(options.soft ?: false).then { UserJs(it) }.asPromise()
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
            object : MembershipsResponseJs {
                override val page: PubNub.MetadataPage
                    get() = object : PubNub.MetadataPage {
                        override var next: String? = it.next?.pageHash
                        override var prev: String? = it.prev?.pageHash
                    }
                override val total: Int
                    get() = it.total
                override val status: Int
                    get() = it.status
                override val memberships: Array<MembershipJs>
                    get() = it.memberships.map { membership ->
                        MembershipJs(membership)
                    }.toTypedArray()
            }
        }.asPromise()
    }

    fun setRestrictions(
        channel: ChannelJs,
        params: RestrictionJs
    ): Promise<Any> {
        return user.setRestrictions(channel.channel, params.ban ?: false, params.mute ?: false, params.reason.toString()).asPromise()
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

@JsExport
@JsName("GetRestrictionsResponseJs")
interface GetRestrictionsResponseJs {
    var page: PubNub.MetadataPage
    var total: Int
    var status: Int
    var restrictions: Array<RestrictionJs>
}

private fun Restriction.asJs(): RestrictionJs {
    val restriction = this
    return createJsObject {
        this.ban = restriction.ban
        this.mute = restriction.mute
        this.reason = restriction.reason
        this.channelId = restriction.channelId
    }
}

@JsExport
@JsName("Restriction")
external interface RestrictionJs {
    var ban: Boolean?
    var mute: Boolean?
    var reason: Any?
    var channelId: String?
}

@JsExport
@JsName("Membership")
class MembershipJs() {
    private lateinit var membership: Membership

    val lastReadMessageTimetoken: String? get() = membership.lastReadMessageTimetoken?.toString()

    @JsExport.Ignore
    internal constructor(membership: Membership) : this() {
        this.membership = membership
    }

    fun update(custom: Any): Promise<MembershipJs> {
        return membership.update(createCustomObject(custom.unsafeCast<JsMap<Any?>>().toMap()))
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

@JsExport
@JsName("MembershipResponse")
external interface MembershipsResponseJs {
    val page: PubNub.MetadataPage
    val total: Int?
    val status: Int
    val memberships: Array<MembershipJs>
}

@JsExport
@JsName("Page")
external interface PageJs {
    val next: String?
    val prev: String?
}

@JsExport
external interface UserFields {
    val id: String
    val name: String?
    val externalId: String?
    val profileUrl: String?
    val email: String?
    val custom: Any?
    val status: String?
    val type: String?
}

@JsExport
external interface ChatConstructor : ChatConfig

@JsExport
external interface ChatConfig {
    val saveDebugLog: Boolean
    val typingTimeout: Int
    val storeUserActivityInterval: Int
    val storeUserActivityTimestamps: Boolean
    val pushNotifications: PushNotificationsConfig
    val rateLimitFactor: Int
    val rateLimitPerChannel: RateLimitPerChannelJs
    val errorLogger: Any?
    val customPayloads: CustomPayloadsJs
}

@JsExport
external interface CustomPayloadsJs

@JsExport
external interface RateLimitPerChannelJs {
    val direct: Int
    val group: Int
    val public: Int
    val unknown: Int
}

@JsExport
external interface DeleteParameters {
    val soft: Boolean?
}

private fun User.asJs() = UserJs(this)

private fun Channel.asJs() = ChannelJs(this)

private fun Membership.asJs() = MembershipJs(this)

private fun Message.asJs() = MessageJs(this)

private fun PubNub.MetadataPage?.toKmp() =
    this?.next?.let { PNPage.PNNext(it) } ?: this?.prev?.let { PNPage.PNPrev(it) }

private fun MetadataPage(next: PNPage.PNNext?, prev: PNPage.PNPrev?) = object : PubNub.MetadataPage {
    override var next: String? = next?.pageHash
    override var prev: String? = prev?.pageHash
}

// @file:OptIn(ExperimentalJsExport::class)
//
// package com.pubnub.chat
//
// import com.pubnub.kmp.PNFuture
// import kotlin.js.Promise
//
fun <T> PNFuture<T>.asPromise(): Promise<T> = Promise { resolve, reject ->
    async {
        it.onSuccess {
            resolve(it)
        }.onFailure {
            reject(it)
        }
    }
}
// //
// // @JsExport
// // @JsName("Chat")
// // class ChatJs constructor(private val chatConfig: ChatConfiguration) {
// //    private val chat: Chat = ChatImpl(chatConfig)
//
// //    fun createUser(
// //        id: String,
// //        userInfo: dynamic
// //    ): Promise<UserJs> {
// //        return GlobalScope.promise {
// //            UserJs(chat.createUser(id,
// //                userInfo.name,
// //                userInfo.externalId,
// //                userInfo.profileUrl,
// //                userInfo.email,
// //                userInfo.custom,
// //                userInfo.status,
// //                userInfo.type))
// //        }
// //    }
// //
// //    fun updateUser(
// //        id: String,
// //        userInfo: dynamic
// //    ): Promise<UserJs> {
// //        return chat.updateUser(
// //            id,
// //            userInfo.name,
// //            userInfo.externalId,
// //            userInfo.profileUrl,
// //            userInfo.email,
// //            userInfo.custom,
// //            userInfo.status,
// //            userInfo.type,
// //        ).then { UserJs(it) }.asPromise()
// //    }
// //
// //    fun deleteUser(id: String, params: dynamic = null): Promise<UserJs> {
// //        return chat.deleteUser(
// //            id,
// //            params?.softDelete ?: false
// //        ).then { UserJs(it) }.asPromise()
// //    }
// // }
//
// // @JsExport
// // @JsName("User")
// // class UserJs internal constructor(private val user: User) {
// //    fun update(
// //        name: String? = null,
// //        externalId: String? = null,
// //        profileUrl: String? = null,
// //        email: String? = null,
// //        custom: CustomObject? = null,
// //        status: String? = null,
// //        type: String? = null,
// //        updated: String? = null, // todo do we need this?
// //    ): Promise<UserJs> {
// //        return user.update(name, externalId, profileUrl, email, custom, status, type)
// //            .then { UserJs(it) }.asPromise()
// //    }
// //
// //    fun delete(softDelete: Boolean): Promise<UserJs> {
// //        return user.delete(softDelete).then { UserJs(it) }.asPromise()
// //    }
// // }
