@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.types.GetFileItem
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.SendTextParams
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.toMap

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
    var mentions: Array<UserMentionJs>
    var enhancedMentionsData: Array<UserMentionDataJs>
    var isMore: Boolean
}

external interface UserMentionJs {
    var message: MessageJs
    var userId: String
    var channelId: String
    var parentChannelId: String?
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

external interface UnreadMessagesCountsJs {
    var page: PubNub.MetadataPage
    var countsByChannel: Array<GetUnreadMessagesCountsJs>
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
    var channels: Array<ChannelJs>
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
    val emitReadReceiptEvents: EmitReadReceiptEventsJs?
    val syncMutedUsers: Boolean?
}

external interface EmitReadReceiptEventsJs {
    val direct: Boolean?
    val group: Boolean?
    val public: Boolean?
    val unknown: Boolean?
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

/**
 * Legacy parameters for sending text messages with rich composition options.
 * @deprecated Use [SendTextParamsJs] with [Channel.sendText] instead.
 */
external interface SendTextOptionParams : PubNub.PublishParameters {
    var mentionedUsers: JsMap<MessageMentionedUser>?
    var referencedChannels: JsMap<MessageReferencedChannel>?
    var textLinks: Array<TextLink>?
    var quotedMessage: MessageJs?
    var files: Any?
    var customPushData: JsMap<String>?
}

/**
 * Legacy parameters for sending text messages with rich composition options.
 * @deprecated Use [SendTextParamsJs] with [Channel.sendText] instead.
 */
external interface SendTextOptionParams : PubNub.PublishParameters {
    var mentionedUsers: JsMap<MessageMentionedUser>?
    var referencedChannels: JsMap<MessageReferencedChannel>?
    var textLinks: Array<TextLink>?
    var quotedMessage: MessageJs?
    var files: Any?
    var customPushData: JsMap<String>?
}

/**
 * Parameters for sending text messages, aligned with Kotlin's SendTextParams.
 *
 * @property meta Additional metadata to include with the message.
 * @property storeInHistory Whether to store the message in history.
 * @property sendByPost Whether to use POST request instead of GET.
 * @property ttl Time-to-live for the message in hours.
 * @property customPushData Custom data to include in push notifications.
 */
external interface SendTextParamsJs {
    val meta: Any?
    val storeInHistory: Boolean?
    val sendByPost: Boolean?
    val ttl: Number?
    val customPushData: JsMap<String>?
}

fun SendTextParamsJs?.toSendTextParams(): SendTextParams {
    return SendTextParams(
        meta = this?.meta?.unsafeCast<JsMap<Any>>()?.toMap(),
        shouldStore = this?.storeInHistory ?: true,
        usePost = this?.sendByPost ?: false,
        ttl = this?.ttl?.toInt(),
        customPushData = this?.customPushData?.toMap(),
    )
}

external interface CustomEventEmitOptions {
    val messageType: String?
    val storeInHistory: Boolean?
}

external interface CustomEventListenOptions {
    val messageType: String?
}

external interface CustomEventData {
    var timetoken: String
    var userId: String
    var payload: Any?
    var type: String?
}

external interface GetHistoryParams {
    val startTimetoken: String?
    val endTimetoken: String?
    val count: Int?
}

external interface GetSuggestionsParams {
    var limit: Int?
}

external interface WhoIsPresentParams {
    val limit: Int?
    val offset: Int?
}

external interface JoinParams {
    val status: String?
    val type: String?
    val custom: PubNub.CustomObject?
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

external interface MentionJs {
    var messageTimetoken: String
    var channelId: String
    var parentChannelId: String?
    var mentionedByUserId: String
}

external interface InviteJs {
    var channelId: String
    var channelType: String
    var invitedByUserId: String
    var invitationTimetoken: String
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

external interface MessageReactionJs {
    var value: String
    var isMine: Boolean
    var userIds: Array<String>
}

external interface ReadReceiptJs {
    var userId: String
    var lastReadTimetoken: String
}

external interface ReadReceiptsResponseJs {
    var page: PubNub.MetadataPage
    var total: Int
    var status: Int
    var receipts: Array<ReadReceiptJs>
}

external interface UpdateMembershipParams {
    val custom: PubNub.CustomObject?
}

/**
 * Result of creating a thread on a message.
 *
 * @property threadChannel The newly created thread channel.
 * @property parentMessage The parent message with updated `hasThread` property set to `true`.
 */
external interface CreateThreadResultJs {
    var threadChannel: ThreadChannelJs
    var parentMessage: MessageJs
}

external interface MessageReportJs {
    var reason: String
    var text: String?
    var messageTimetoken: String?
    var reportedMessageChannelId: String?
    var reportedUserId: String?
    var autoModerationId: String?
}
