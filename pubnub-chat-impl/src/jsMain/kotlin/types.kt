@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.types.GetFileItem
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.createJsObject

external interface GetEventsHistoryParams {
    val channel: String
    val startTimetoken: String?
    val endTimetoken: String?
    val count: Number?
}

external interface MarkAllMessageAsReadResponseJs {
    var memberships: Array<MembershipJs>
    var page: PubNub.MetadataPage
    var total: Int?
    var status: Int
}

external interface GetCurrentUserMentionsResultJs {
    var enhancedMentionsData: Array<UserMentionDataJs>
    var isMore: Boolean
}

external interface UserMentionDataJs {
    val event: EventJs
    val userId: String
    val message: BaseMessageJs
}

external interface ChannelMentionDataJs : UserMentionDataJs {
    override var event: EventJs
    override var userId: String
    override var message: BaseMessageJs
    var channelId: String
}

external interface ThreadMentionDataJs : UserMentionDataJs {
    override var event: EventJs
    override var userId: String
    override var message: BaseMessageJs
    var parentChannelId: String
    var threadChannelId: String
}

external interface GetUnreadMessagesCountsJs {
    var channel: BaseChannelJs
    var membership: MembershipJs
    var count: Double
}

external interface CreateGroupConversationResultJs {
    var channel: BaseChannelJs
    var hostMembership: MembershipJs
    var inviteesMemberships: Array<MembershipJs>
}

external interface CreateDirectConversationResultJs {
    var channel: BaseChannelJs
    var hostMembership: MembershipJs
    var inviteeMembership: MembershipJs
}

external interface GetChannelsResponseJs {
    var channels: Array<BaseChannelJs>
    var page: PubNub.MetadataPage
    var total: Int
}

external interface ChannelFields {
    val id: String
    val name: String?
    val custom: Any?
    val description: String?
    val updated: String?
    val status: String?
    val type: String?
}

external interface GetUsersResponseJs {
    var users: Array<UserJs>
    var page: PubNub.MetadataPage
    var total: Int
}

external interface GetEventsHistoryResultJs {
    var events: Array<EventJs>
    var isMore: Boolean
}

external interface PushNotificationsConfigJs {
    val sendPushes: Boolean
    val deviceToken: String?
    val deviceGateway: String
    val apnsTopic: String?
    val apnsEnvironment: String
}

external interface GetFilesResultJs {
    var files: Array<GetFileItem>
    var next: String?
    var total: Int
}

external interface MembersResponseJs {
    var page: PubNub.MetadataPage
    var total: Int?
    var status: Int
    var members: Array<MembershipJs>
}

external interface HistoryResponseJs {
    var messages: Array<BaseMessageJs>
    var isMore: Boolean
}

external interface GetRestrictionsResponseJs {
    var page: PubNub.MetadataPage
    var total: Int
    var status: Int
    var restrictions: Array<RestrictionJs>
}

external interface RestrictionJs {
    var ban: Boolean?
    var mute: Boolean?
    var reason: Any?
    var channelId: String?
    var userId: String?
}

external interface MembershipsResponseJs {
    var page: PubNub.MetadataPage
    var total: Int?
    var status: Int
    var memberships: Array<MembershipJs>
}

external interface PageJs {
    val next: String?
    val prev: String?
}

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

external interface ChatConstructor : ChatConfig

external interface ChatConfig {
    val saveDebugLog: Boolean?
    val typingTimeout: Int?
    val storeUserActivityInterval: Int?
    val storeUserActivityTimestamps: Boolean?
    val pushNotifications: PushNotificationsConfigJs?
    val rateLimitFactor: Int?
    val rateLimitPerChannel: RateLimitPerChannelJs?
    val errorLogger: Any?
    val customPayloads: CustomPayloadsJs?
    val syncMutedUsers: Boolean?
}

external interface CustomPayloadsJs {
    val getMessagePublishBody: ((JsMap<Any?>, String) -> Any)?
    val getMessageResponseBody: ((JsMap<Any?>) -> Any)?
    val editMessageActionName: String?
    val deleteMessageActionName: String?
    val reactionsActionName: String?
}

external interface RateLimitPerChannelJs {
    val direct: Int
    val group: Int
    val public: Int
    val unknown: Int
}

external interface DeleteParameters {
    val soft: Boolean?
}

external interface MessageDraftConfig {
    var userSuggestionSource: String?
    var isTypingIndicatorTriggered: Boolean?
    var userLimit: Int?
    var channelLimit: Int?
}

external interface SendTextOptionParams : PubNub.PublishParameters {
    var mentionedUsers: JsMap<MessageMentionedUser>?
    var referencedChannels: JsMap<MessageReferencedChannel>?
    var textLinks: Array<TextLink>?
    var quotedMessage: BaseMessageJs?
    var files: Any?
    var customPushData: JsMap<String>?
}

external interface GetHistoryParams {
    val startTimetoken: String?
    val endTimetoken: String?
    val count: Int?
}

external interface GetSuggestionsParams {
    var limit: Int?
}

external interface DeleteUserResult

@Suppress("NOTHING_TO_INLINE")
inline fun DeleteUserResult(user: UserJs): DeleteUserResult {
    return user.unsafeCast<DeleteUserResult>()
}

@Suppress("NOTHING_TO_INLINE")
inline fun DeleteUserResult(boolean: Boolean): DeleteUserResult {
    return boolean.unsafeCast<DeleteUserResult>()
}

external interface DeleteChannelResult

@Suppress("NOTHING_TO_INLINE")
inline fun DeleteChannelResult(channel: BaseChannelJs): DeleteChannelResult {
    return channel.unsafeCast<DeleteChannelResult>()
}

@Suppress("NOTHING_TO_INLINE")
inline fun DeleteChannelResult(boolean: Boolean): DeleteChannelResult {
    return boolean.unsafeCast<DeleteChannelResult>()
}

external interface JoinResultJs {
    var membership: MembershipJs
    var disconnect: () -> Unit
}

external interface DeleteFileParams {
    val id: String
    val name: String
}

external interface GetMessageReportsHistoryResult {
    var events: Array<EventJs>
    var isMore: Boolean
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun PNPublishResult.toPublishResponse(): PubNub.PublishResponse =
    createJsObject<PubNub.PublishResponse> { timetoken = this@toPublishResponse.timetoken.toString() }

external interface CreateChannelParams {
    val channelId: String?
    val channelData: PubNub.ChannelMetadata?
}

external interface CreateDirectConversationParams {
    val user: UserJs
    val channelId: String?
    val channelData: PubNub.ChannelMetadata?
    val membershipData: SetMembershipsParametersAndCustom?
}

external interface CreateGroupConversationParams {
    val users: Array<UserJs>
    val channelId: String?
    val channelData: PubNub.ChannelMetadata?
    val membershipData: SetMembershipsParametersAndCustom?
}

external interface SetMembershipsParametersAndCustom : PubNub.SetMembershipsParameters {
    val custom: PubNub.CustomObject?
}

external interface ListenForEventsParams {
    val type: String
    val channel: String?
    val user: String?
    val method: String?
    val callback: (EventJs) -> Any
}

external interface GetUnreadMessagesCountResult

@Suppress("NOTHING_TO_INLINE")
inline fun GetUnreadMessagesCountResult(number: Int): GetUnreadMessagesCountResult {
    return number.unsafeCast<GetUnreadMessagesCountResult>()
}

@Suppress("NOTHING_TO_INLINE")
inline fun GetUnreadMessagesCountResult(boolean: Boolean): GetUnreadMessagesCountResult {
    return boolean.unsafeCast<GetUnreadMessagesCountResult>()
}

external interface Reaction {
    var uuid: String
    var actionTimetoken: String
}

external interface UpdateMembershipParams {
    val custom: PubNub.CustomObject?
}
