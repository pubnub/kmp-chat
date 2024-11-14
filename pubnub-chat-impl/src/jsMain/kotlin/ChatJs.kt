@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.PubNubImpl
import com.pubnub.api.createJsonElement
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelMentionData
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateDirectConversationResult
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.ThreadMentionData
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("Chat")
class ChatJs internal constructor(val chat: ChatInternal, val config: ChatConfig) {
    val currentUser: UserJs get() = chat.currentUser.asJs()

    val sdk: PubNub get() = (chat.pubNub as PubNubImpl).jsPubNub

    fun emitEvent(event: dynamic): Promise<PubNub.SignalResponse> {
        val channel: String = event.channel ?: event.user
        val type = event.type
        val payload = event.payload
        payload.type = type
        return chat.emitEvent(
            channel,
            PNDataEncoder.decode(createJsonElement(payload))
        ).then { result ->
            createJsObject<PubNub.SignalResponse> { timetoken = result.timetoken.toString() }
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
        return chat.getUser(id).then { it?.asJs() }.asPromise()
    }

    fun createUser(id: String, data: UserFields): Promise<UserJs> {
        return chat.createUser(
            id,
            data.name,
            data.externalId,
            data.profileUrl,
            data.email,
            convertToCustomObject(data.custom),
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
            convertToCustomObject(data.custom),
            data.status,
            data.type
        ).then { it.asJs() }.asPromise()
    }

    fun deleteUser(id: String, params: DeleteParameters?): Promise<Any> {
        return chat.deleteUser(id, params?.soft ?: false).then { it?.asJs() ?: true }.asPromise()
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
            convertToCustomObject(data.custom), // TODO
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

    fun deleteChannel(id: String, params: DeleteParameters?): Promise<Any> {
        return chat.deleteChannel(id, params?.soft ?: false).then { it?.asJs() ?: true }.asPromise()
    }

    // internal
    fun createChannel(id: String, data: dynamic): Promise<ChannelJs> {
        return (chat as ChatInternal).createChannel(
            id,
            data?.name,
            data?.description,
            convertToCustomObject(data?.custom), // TODO
            ChannelType.from(data?.type),
            data?.status
        ).then { it.asJs() }.asPromise()
    }

    // internal
    fun getThreadId(channelId: String, messageId: String) =
        ChatImpl.getThreadId(channelId, messageId.toLong())

    fun createPublicConversation(params: dynamic): Promise<ChannelJs> {
        val channelId: String? = params.channelId
        val data: PubNub.ChannelMetadata? = params.channelData
        return chat.createPublicConversation(
            channelId,
            data?.name,
            data?.description,
            convertToCustomObject(data?.custom), // TODO
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
            convertToCustomObject(data?.custom), // TODO
            data?.status,
            convertToCustomObject(membershipCustom), // TODO
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
            convertToCustomObject(data?.custom), // TODO
            data?.status,
            convertToCustomObject(membershipCustom), // TODO
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
        params: dynamic,
        /*params?: {
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

    fun getUnreadMessagesCounts(params: PubNub.GetMembershipsParametersv2?): Promise<Array<GetUnreadMessagesCountsJs>> {
        return chat.getUnreadMessagesCounts(
            params?.limit?.toInt(),
            params?.page?.toKmp(),
            params?.filter,
            extractSortKeys(params?.sort)
        ).then { result ->
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

    fun toJSON(): Json {
        return json("config" to config, "currentUser" to currentUser)
    }

    fun getUserSuggestions(text: String, options: dynamic?): Promise<Array<UserJs>> {
        val limit = options?.limit as? Number
        return chat.getUserSuggestions(text.substring(1), limit?.toInt() ?: 10).then { users ->
            users.map { it.asJs() }.toTypedArray()
        }.asPromise()
    }

    companion object {
        @JsStatic
        fun init(config: ChatConstructor): Promise<ChatJs> {
            return ChatImpl(config.toChatConfiguration(), PubNubImpl(PubNub(config))).initialize().then {
                ChatJs(it as ChatInternal, config)
            }.asPromise()
        }
    }
}

@JsExport
val INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_"
