@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.types.GetFileItem
import com.pubnub.kmp.JsMap

external interface GetEventsHistoryParams {
    val channel: String
    val startTimetoken: String?
    val endTimetoken: String?
    val count: Number?
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
@JsName("QuotedMessage")
interface QuotedMessageJs {
    var timetoken: String
    var text: String
    var userId: String
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
@JsName("PushNotificationsConfig")
external interface PushNotificationsConfigJs {
    val sendPushes: Boolean
    val deviceToken: String?
    val deviceGateway: String
    val apnsTopic: String?
    val apnsEnvironment: String
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
@JsName("GetRestrictionsResponse")
interface GetRestrictionsResponseJs {
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
}

@JsExport
@JsName("MembershipResponse")
external interface MembershipsResponseJs {
    var page: PubNub.MetadataPage
    var total: Int?
    var status: Int
    var memberships: Array<MembershipJs>
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
    val saveDebugLog: Boolean?
    val typingTimeout: Int?
    val storeUserActivityInterval: Int?
    val storeUserActivityTimestamps: Boolean?
    val pushNotifications: PushNotificationsConfigJs?
    val rateLimitFactor: Int?
    val rateLimitPerChannel: RateLimitPerChannelJs?
    val errorLogger: Any?
    val customPayloads: CustomPayloadsJs?
}

@JsExport
@JsName("CustomPayloads")
external interface CustomPayloadsJs {
    val getMessagePublishBody: ((JsMap<Any?>, String) -> Any)?
    val getMessageResponseBody: ((JsMap<Any?>) -> Any)?
    val editMessageActionName: String?
    val deleteMessageActionName: String?
    val reactionsActionName: String?
}

@JsExport
@JsName("RateLimitPerChannel")
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

@JsExport
external interface MessageDraftConfig {
    val userSuggestionSource: String?
    val isTypingIndicatorTriggered: Boolean?
    val userLimit: Int?
    val channelLimit: Int?
}

@JsExport
val CryptoModule = PubNub.CryptoModule
