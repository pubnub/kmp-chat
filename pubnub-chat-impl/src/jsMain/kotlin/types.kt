@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.types.GetFileItem
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.JsMap

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
    val message: MessageJs
}
external interface ChannelMentionDataJs : UserMentionDataJs {
    override var event: EventJs
    override var userId: String
    override var message: MessageJs
    var channelId: String
}

external interface ThreadMentionDataJs : UserMentionDataJs {
    override var event: EventJs
    override var userId: String
    override var message: MessageJs
    var parentChannelId: String
    var threadChannelId: String
}

external interface GetUnreadMessagesCountsJs {
    var channel: ChannelJs
    var membership: MembershipJs
    var count: Double
}

external interface QuotedMessageJs {
    var timetoken: String
    var text: String
    var userId: String
}

external interface CreateGroupConversationResultJs {
    var channel: ChannelJs
    var hostMembership: MembershipJs
    var inviteesMemberships: Array<MembershipJs>
}

external interface CreateDirectConversationResultJs {
    var channel: ChannelJs
    var hostMembership: MembershipJs
    var inviteeMembership: MembershipJs
}

external interface GetChannelsResponseJs {
    var users: Array<ChannelJs>
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
    var messages: Array<MessageJs>
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
    var quotedMessage: MessageJs?
    var files: Any?
}

external interface GetHistoryParams {
    val startTimetoken: String?
    val endTimetoken: String?
    val count: Int?
}

external interface GetSuggestionsParams {
    var limit: Int?
}
