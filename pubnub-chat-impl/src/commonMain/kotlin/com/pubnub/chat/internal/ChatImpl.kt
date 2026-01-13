package com.pubnub.chat.internal

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.asMap
import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.enums.PNPushType
import com.pubnub.api.enums.PNStatusCategory
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.membership.MembershipInclude
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.models.consumer.pubsub.MessageResult
import com.pubnub.api.models.consumer.pubsub.PNEvent
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushListProvisionsResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.api.utils.Clock
import com.pubnub.api.utils.Instant
import com.pubnub.api.utils.TimetokenUtil
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.callbacks.StatusListener
import com.pubnub.chat.Channel
import com.pubnub.chat.ChannelGroup
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.LogLevel
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.internal.channelGroup.ChannelGroupImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.internal.error.PubNubErrorMessage.APNS_TOPIC_SHOULD_BE_DEFINED_WHEN_DEVICE_GATEWAY_IS_SET_TO_APNS2
import com.pubnub.chat.internal.error.PubNubErrorMessage.CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL
import com.pubnub.chat.internal.error.PubNubErrorMessage.CAN_NOT_FIND_CHANNEL_WITH_ID
import com.pubnub.chat.internal.error.PubNubErrorMessage.CHANNEL_ID_ALREADY_EXIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.CHANNEL_ID_IS_REQUIRED
import com.pubnub.chat.internal.error.PubNubErrorMessage.CHANNEL_NOT_EXIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.CHANNEL_NOT_FOUND
import com.pubnub.chat.internal.error.PubNubErrorMessage.COUNT_SHOULD_NOT_EXCEED_100
import com.pubnub.chat.internal.error.PubNubErrorMessage.DEVICE_TOKEN_HAS_TO_BE_DEFINED_IN_CHAT_PUSHNOTIFICATIONS_CONFIG
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_CHANNEL_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_USER_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_FORWARD_MESSAGE
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_SOFT_DELETE_CHANNEL
import com.pubnub.chat.internal.error.PubNubErrorMessage.ID_IS_REQUIRED
import com.pubnub.chat.internal.error.PubNubErrorMessage.MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY
import com.pubnub.chat.internal.error.PubNubErrorMessage.STORE_USER_ACTIVITY_INTERVAL_SHOULD_BE_AT_LEAST_1_MIN
import com.pubnub.chat.internal.error.PubNubErrorMessage.THERE_IS_NO_ACTION_TIMETOKEN_CORRESPONDING_TO_THE_THREAD
import com.pubnub.chat.internal.error.PubNubErrorMessage.THERE_IS_NO_THREAD_TO_BE_DELETED
import com.pubnub.chat.internal.error.PubNubErrorMessage.THERE_IS_NO_THREAD_WITH_ID
import com.pubnub.chat.internal.error.PubNubErrorMessage.THIS_MESSAGE_IS_NOT_A_THREAD
import com.pubnub.chat.internal.error.PubNubErrorMessage.THIS_THREAD_ID_ALREADY_RESTORED
import com.pubnub.chat.internal.error.PubNubErrorMessage.USER_ID_ALREADY_EXIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.USER_NOT_EXIST
import com.pubnub.chat.internal.mutelist.MutedUsersManagerImpl
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.internal.timer.PlatformTimer
import com.pubnub.chat.internal.timer.TimerManager
import com.pubnub.chat.internal.timer.createTimerManager
import com.pubnub.chat.internal.util.channelsUrlDecoded
import com.pubnub.chat.internal.util.logErrorAndReturnException
import com.pubnub.chat.internal.util.nullOn404
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.internal.utils.cyrb53a
import com.pubnub.chat.listeners.ConnectionStatus
import com.pubnub.chat.listeners.ConnectionStatusCategory.PN_CONNECTION_ERROR
import com.pubnub.chat.listeners.ConnectionStatusCategory.PN_CONNECTION_OFFLINE
import com.pubnub.chat.listeners.ConnectionStatusCategory.PN_CONNECTION_ONLINE
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.message.MarkAllMessageAsReadResponse
import com.pubnub.chat.message.UnreadMessagesCounts
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.restrictions.RestrictionType
import com.pubnub.chat.types.ChannelMentionData
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateDirectConversationResult
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.types.GetCurrentUserMentionsResult
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.types.MessageActionType
import com.pubnub.chat.types.ThreadMentionData
import com.pubnub.chat.types.UserMentionData
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.catch
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.createStatusListener
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import encodeForSending
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ChatImpl(
    override val config: ChatConfiguration,
    override val pubNub: PubNub,
    override val editMessageActionName: String = config.customPayloads?.editMessageActionName
        ?: MessageActionType.EDITED.toString(),
    override val deleteMessageActionName: String = config.customPayloads?.deleteMessageActionName
        ?: MessageActionType.DELETED.toString(),
    override val reactionsActionName: String = config.customPayloads?.reactionsActionName
        ?: MessageActionType.REACTIONS.toString(),
    override val timerManager: TimerManager = createTimerManager()
) : ChatInternal {
    override var currentUser: User =
        UserImpl(this, pubNub.configuration.userId.value, name = pubNub.configuration.userId.value)
        private set

    override val mutedUsersManager =
        MutedUsersManagerImpl(pubNub, pubNub.configuration.userId.value, config.syncMutedUsers)

    private val suggestedChannelsCache: MutableMap<String, List<Channel>> = mutableMapOf()
    private val suggestedUsersCache: MutableMap<String, List<User>> = mutableMapOf()

    private var lastSavedActivityInterval: PlatformTimer? = null
    private var runWithDelayTimer: PlatformTimer? = null

    @Volatile
    private var connectionStatusListenersMap: Map<String, StatusListener> = emptyMap()
    private val connectionStatusListenersLock = SynchronizedObject()

    init {
        Logger.setMinSeverity(mapLogLevelFromConfigToKmLogging())

        if (config.storeUserActivityInterval < 60.seconds) {
            log.pnError(STORE_USER_ACTIVITY_INTERVAL_SHOULD_BE_AT_LEAST_1_MIN)
        }

        if (config.pushNotifications.deviceGateway == PNPushType.APNS2 && config.pushNotifications.apnsTopic == null) {
            log.pnError(APNS_TOPIC_SHOULD_BE_DEFINED_WHEN_DEVICE_GATEWAY_IS_SET_TO_APNS2)
        }
    }

    fun initialize(): PNFuture<Chat> {
        val userFuture = getUser(pubNub.configuration.userId.value).thenAsync { user ->
            user?.asFuture() ?: createUser(currentUser)
        }.then { user ->
            currentUser = user
        }.thenAsync { _: Unit ->
            if (config.storeUserActivityTimestamps) {
                storeUserActivityTimestamp()
            } else {
                Unit.asFuture()
            }
        }
        val mutedUsersFuture = mutedUsersManager.loadMutedUsers()
        return awaitAll(userFuture, mutedUsersFuture).then {
            this
        }
    }

    override fun createUser(user: User): PNFuture<User> = createUser(
        id = user.id,
        name = user.name,
        externalId = user.externalId,
        profileUrl = user.profileUrl,
        email = user.email,
        custom = user.custom?.let { createCustomObject(it) },
        status = user.status,
        type = user.type
    )

    override fun restoreThreadChannel(message: Message): PNFuture<PNMessageAction?> {
        val threadChannelId = getThreadId(message.channelId, message.timetoken)
        return getChannel(threadChannelId).thenAsync { channel: Channel? ->
            if (channel == null) {
                null.asFuture()
            } else {
                // message.delete(soft=true) return message that has "actions" that contain THREAD_ROOT_ID
                // on the other hand when you call channel01.getMessage() after message was deleted(soft=true)
                // then returned message has "actions" that doesn't contain THREAD_ROOT_ID so we can't rely on
                // THREAD_ROOT_ID to decide whether thread has been already restored or not. To do this we use DELETED.
                if (!message.deleted) {
                    log.pnError(THIS_THREAD_ID_ALREADY_RESTORED)
                }

                val messageAction = PNMessageAction(
                    type = THREAD_ROOT_ID,
                    value = threadChannelId,
                    messageTimetoken = message.timetoken
                )
                pubNub.addMessageAction(channel = message.channelId, messageAction = messageAction)
                // we don't update action map here but we do this in message#restore()
            }
        }
    }

    override fun createUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?,
    ): PNFuture<User> {
        if (!isValidId(id)) {
            return log.logErrorAndReturnException(ID_IS_REQUIRED).asFuture()
        }

        return getUser(id).thenAsync { user: User? ->
            if (user != null) {
                log.pnError(USER_ID_ALREADY_EXIST)
            } else {
                setUserMetadata(
                    id = id,
                    name = name,
                    externalId = externalId,
                    profileUrl = profileUrl,
                    email = email,
                    custom = custom,
                    type = type,
                    status = status
                )
            }
        }
    }

    override fun removeThreadChannel(
        chat: Chat,
        message: Message,
        soft: Boolean
    ): PNFuture<Pair<PNRemoveMessageActionResult, Channel?>> {
        // get message to make sure that message data are up to date e.g. message.hasThread
        return BaseChannel.getMessage(chat = this, channelId = message.channelId, timetoken = message.timetoken)
            .thenAsync { msg: Message? ->
                if (msg?.hasThread != true) {
                    return@thenAsync PubNubException(THERE_IS_NO_THREAD_TO_BE_DELETED)
                        .logErrorAndReturnException(log).asFuture()
                }

                val threadId = getThreadId(msg.channelId, msg.timetoken)

                val actionTimetoken =
                    msg.actions?.get(THREAD_ROOT_ID)?.get(threadId)?.get(0)?.actionTimetoken
                        ?: return@thenAsync PubNubException(THERE_IS_NO_ACTION_TIMETOKEN_CORRESPONDING_TO_THE_THREAD)
                            .logErrorAndReturnException(log).asFuture()

                chat.getChannel(threadId).thenAsync { threadChannel ->
                    if (threadChannel == null) {
                        return@thenAsync PubNubException("$THERE_IS_NO_THREAD_WITH_ID$threadId")
                            .logErrorAndReturnException(log).asFuture()
                    }
                    awaitAll(
                        chat.pubNub.removeMessageAction(msg.channelId, msg.timetoken, actionTimetoken),
                        threadChannel.delete(soft)
                    )
                }
            }
    }

    override fun getUser(userId: String): PNFuture<User?> {
        if (!isValidId(userId)) {
            return log.logErrorAndReturnException(ID_IS_REQUIRED).asFuture()
        }

        return pubNub.getUUIDMetadata(uuid = userId, includeCustom = true)
            .then { pnUUIDMetadataResult: PNUUIDMetadataResult ->
                UserImpl.fromDTO(this, pnUUIDMetadataResult.data)
            }.nullOn404()
    }

    override fun getUsers(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): PNFuture<GetUsersResponse> {
        return pubNub.getAllUUIDMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).then { pnUUIDMetadataArrayResult: PNUUIDMetadataArrayResult ->
            val users = pnUUIDMetadataArrayResult.data.map { pnUUIDMetadata ->
                UserImpl.fromDTO(this, pnUUIDMetadata)
            }
            GetUsersResponse(
                users = users,
                next = pnUUIDMetadataArrayResult.next,
                prev = pnUUIDMetadataArrayResult.prev,
                total = pnUUIDMetadataArrayResult.totalCount ?: 0
            )
        }.catch {
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_GET_USERS, it))
        }
    }

    override fun updateUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?
    ): PNFuture<User> {
        if (!isValidId(id)) {
            return log.logErrorAndReturnException(ID_IS_REQUIRED).asFuture()
        }

        return getUser(id).thenAsync { user ->
            if (user != null) {
                setUserMetadata(id, name, externalId, profileUrl, email, custom, type, status)
            } else {
                log.pnError(USER_NOT_EXIST)
            }
        }
    }

    override fun deleteUser(id: String, soft: Boolean): PNFuture<User?> {
        if (!isValidId(id)) {
            return PubNubException(ID_IS_REQUIRED).logErrorAndReturnException(log).asFuture()
        }

        return if (soft) {
            getUser(id).thenAsync { user: User? ->
                if (user == null) {
                    log.pnError(USER_NOT_EXIST)
                }
                performSoftUserDelete(user)
            }
        } else {
            performUserDelete(id).then { null }
        }
    }

    override fun wherePresent(userId: String): PNFuture<List<String>> {
        if (!isValidId(userId)) {
            return log.logErrorAndReturnException(ID_IS_REQUIRED).asFuture()
        }

        return pubNub.whereNow(uuid = userId).then { pnWhereNowResult ->
            pnWhereNowResult.channels
        }.catch { pnException ->
            Result.Companion.failure(
                PubNubException(
                    PubNubErrorMessage.FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA,
                    pnException
                )
            )
        }
    }

    override fun isPresent(userId: String, channelId: String): PNFuture<Boolean> {
        if (!isValidId(userId)) {
            return log.logErrorAndReturnException(ID_IS_REQUIRED).asFuture()
        }
        if (!isValidId(channelId)) {
            return log.logErrorAndReturnException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }

        return pubNub.whereNow(uuid = userId).then { pnWhereNowResult ->
            pnWhereNowResult.channels.contains(channelId)
        }.catch { pnException ->
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_IS_PRESENT_DATA, pnException))
        }
    }

    override fun createChannel(
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?
    ): PNFuture<Channel> {
        if (!isValidId(id)) {
            return log.logErrorAndReturnException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return getChannel(id).thenAsync { channel: Channel? ->
            if (channel != null) {
                log.pnError(CHANNEL_ID_ALREADY_EXIST)
            } else {
                setChannelMetadata(id, name, description, custom, type, status)
            }
        }
    }

    override fun getChannels(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?
    ): PNFuture<GetChannelsResponse> {
        return pubNub.getAllChannelMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).then { pnChannelMetadataArrayResult ->
            val channels = pnChannelMetadataArrayResult.data.map { pnChannelMetadata ->
                ChannelImpl.fromDTO(this, pnChannelMetadata)
            }
            GetChannelsResponse(
                channels = channels,
                next = pnChannelMetadataArrayResult.next,
                prev = pnChannelMetadataArrayResult.prev,
                total = pnChannelMetadataArrayResult.totalCount ?: 0
            )
        }.catch { exception ->
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_GET_CHANNELS, exception))
        }
    }

    override fun getChannel(channelId: String): PNFuture<Channel?> {
        if (!isValidId(channelId)) {
            return log.logErrorAndReturnException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return pubNub.getChannelMetadata(channel = channelId, includeCustom = true)
            .then { pnChannelMetadataResult: PNChannelMetadataResult ->
                ChannelImpl.fromDTO(this, pnChannelMetadataResult.data)
            }.nullOn404()
    }

    override fun updateChannel(
        id: String,
        name: String?,
        custom: CustomObject?,
        description: String?,
        status: String?,
        type: ChannelType?
    ): PNFuture<Channel> {
        if (!isValidId(id)) {
            return log.logErrorAndReturnException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }

        return getChannel(id).thenAsync { channel: Channel? ->
            if (channel != null) {
                setChannelMetadata(id, name, description, custom, type, status)
            } else {
                log.pnError(CHANNEL_NOT_FOUND)
            }
        }
    }

    override fun deleteChannel(id: String, soft: Boolean): PNFuture<Channel?> {
        if (!isValidId(id)) {
            return log.logErrorAndReturnException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }

        return if (soft) {
            getChannel(id).thenAsync { channel ->
                if (channel == null) {
                    log.pnError(CHANNEL_NOT_EXIST)
                }
                performSoftChannelDelete(channel)
            }
        } else {
            performChannelDelete(id).then { null }
        }
    }

    override fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult> {
        if (!isValidId(channelId)) {
            return log.logErrorAndReturnException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        if (message.channelId == channelId) {
            return log.logErrorAndReturnException(CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL).asFuture()
        }

        val meta = message.meta?.toMutableMap() ?: mutableMapOf()
        meta[ORIGINAL_PUBLISHER] = message.userId
        meta[ORIGINAL_CHANNEL_ID] = message.channelId

        return pubNub.publish(
            message = message.content.encodeForSending(channelId, config.customPayloads?.getMessagePublishBody),
            channel = channelId,
            meta = meta
        ).catch { exception ->
            Result.failure(PubNubException(FAILED_TO_FORWARD_MESSAGE, exception))
        }
    }

    override fun <T : EventContent> emitEvent(
        channelId: String,
        payload: T,
        mergePayloadWith: Map<String, Any>?
    ): PNFuture<PNPublishResult> {
        val emitMethod = payload::class.getEmitMethod() ?: (payload as? EventContent.Custom)?.method
        return if (emitMethod == EmitEventMethod.SIGNAL) {
            pubNub.signal(
                channel = channelId,
                message = payload.encodeForSending(mergePayloadWith),
                customMessageType = payload.customMessageType
            )
        } else {
            pubNub.publish(
                channel = channelId,
                message = payload.encodeForSending(mergePayloadWith),
                customMessageType = payload.customMessageType
            )
        }
    }

    override fun createPublicConversation(
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?
    ): PNFuture<Channel> {
        val finalChannelId: String = channelId ?: generateRandomUuid()

        return createChannel(
            id = finalChannelId,
            name = channelName ?: finalChannelId,
            description = channelDescription,
            custom = channelCustom,
            type = ChannelType.PUBLIC,
            status = channelStatus
        )
    }

    override fun createDirectConversation(
        invitedUser: User,
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?,
        membershipCustom: CustomObject?,
    ): PNFuture<CreateDirectConversationResult> {
        val user = this.currentUser
        val sortedUsers = listOf(invitedUser.id, user.id).sorted()
        val finalChannelId = channelId ?: "direct.${cyrb53a("${sortedUsers[0]}&${sortedUsers[1]}")}"

        return getChannel(finalChannelId).thenAsync { channel ->
            channel?.asFuture() ?: createChannel(
                finalChannelId,
                channelName ?: finalChannelId,
                channelDescription,
                channelCustom,
                ChannelType.DIRECT,
                channelStatus
            )
        }.thenAsync { channel: Channel ->
            val hostMembershipFuture = pubNub.setMemberships(
                listOf(PNChannelMembership.Partial(channel.id, membershipCustom)),
                filter = "channel.id == '${channel.id}'",
                include = MembershipInclude(
                    includeCustom = true,
                    includeStatus = false,
                    includeType = false,
                    includeTotalCount = true,
                    includeChannel = true,
                    includeChannelCustom = true,
                    includeChannelType = true,
                    includeChannelStatus = false
                ),
            )
            awaitAll(
                hostMembershipFuture,
                channel.invite(invitedUser)
            ).then { (hostMembershipResponse: PNChannelMembershipArrayResult, inviteeMembership: Membership) ->
                CreateDirectConversationResult(
                    channel,
                    MembershipImpl.fromMembershipDTO(
                        this,
                        hostMembershipResponse.data.first(),
                        user
                    ),
                    inviteeMembership,
                )
            }
        }
    }

    override fun createGroupConversation(
        invitedUsers: Collection<User>,
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?,
        membershipCustom: CustomObject?
    ): PNFuture<CreateGroupConversationResult> {
        val user = this.currentUser
        val finalChannelId = channelId ?: generateRandomUuid()
        return getChannel(finalChannelId).thenAsync { channel ->
            channel?.asFuture() ?: createChannel(
                finalChannelId,
                channelName ?: finalChannelId,
                channelDescription,
                channelCustom,
                ChannelType.GROUP,
                channelStatus
            )
        }.thenAsync { channel: Channel ->
            val hostMembershipFuture = pubNub.setMemberships(
                listOf(PNChannelMembership.Partial(channel.id, membershipCustom)),
                filter = "channel.id == '${channel.id}'",
                include = MembershipInclude(
                    includeCustom = true,
                    includeStatus = false,
                    includeType = false,
                    includeTotalCount = true,
                    includeChannel = true,
                    includeChannelCustom = true,
                    includeChannelType = true,
                    includeChannelStatus = false
                ),
            )
            awaitAll(
                hostMembershipFuture,
                channel.inviteMultiple(invitedUsers)
            ).then { (hostMembershipResponse: PNChannelMembershipArrayResult, inviteeMemberships: List<Membership>) ->
                CreateGroupConversationResult(
                    channel,
                    MembershipImpl.fromMembershipDTO(
                        this,
                        hostMembershipResponse.data.first(),
                        user
                    ),
                    inviteeMemberships.toTypedArray(),
                )
            }
        }
    }

    override fun whoIsPresent(channelId: String, limit: Int, offset: Int?): PNFuture<Collection<String>> {
        if (!isValidId(channelId)) {
            return log.logErrorAndReturnException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return pubNub.hereNow(
            channels = listOf(channelId),
            limit = limit,
            offset = offset
        ).then {
            (it.channels[channelId]?.occupants?.map(PNHereNowOccupantData::uuid) ?: emptyList())
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA, exception))
        }
    }

    override fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channelId: String,
        customMethod: EmitEventMethod,
        callback: (event: Event<T>) -> Unit
    ): AutoCloseable {
        val handler = fun(_: PubNub, pnEvent: PNEvent) {
            try {
                if (pnEvent.channel != channelId) {
                    return
                }
                val message = (pnEvent as? MessageResult)?.message ?: return
                if (pnEvent.publisher in mutedUsersManager.mutedUsers) {
                    return
                }

                val eventContent: EventContent = try {
                    PNDataEncoder.decode<EventContent>(message)
                } catch (e: Exception) {
                    if (message.asMap()?.get(TYPE_OF_MESSAGE)?.asString() == TYPE_OF_MESSAGE_IS_CUSTOM) {
                        EventContent.Custom((message.decode() as Map<String, Any?>) - TYPE_OF_MESSAGE)
                    } else {
                        throw e
                    }
                }

                val payload: T = if (type.isInstance(eventContent)) {
                    eventContent as T
                } else {
                    return
                }

                val event = EventImpl(
                    chat = this,
                    timetoken = pnEvent.timetoken!!,
                    payload = payload,
                    channelId = pnEvent.channel,
                    userId = pnEvent.publisher!!
                )
                callback(event)
            } catch (e: Exception) {
                log.e(throwable = e) { e.message.orEmpty() }
            }
        }
        val method = type.getEmitMethod() ?: customMethod
        val listener = createEventListener(
            pubNub,
            onMessage = if (method == EmitEventMethod.PUBLISH) {
                handler
            } else {
                { _, _ -> }
            },
            onSignal = if (method == EmitEventMethod.SIGNAL) {
                handler
            } else {
                { _, _ -> }
            },
        )
        val channelEntity = pubNub.channel(channelId)
        val subscription = channelEntity.subscription()
        subscription.addListener(listener)
        subscription.subscribe()
        return subscription
    }

    override fun setRestrictions(
        restriction: Restriction
    ): PNFuture<Unit> {
        if (this.pubNub.configuration.secretKey.isEmpty()) {
            return log.logErrorAndReturnException(MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY).asFuture()
        }
        val channel: String = INTERNAL_MODERATION_PREFIX + restriction.channelId
        val userId = restriction.userId
        // if "Enforce referential integrity for memberships" is enabled we need to make sure that channel exists in AppContext
        return createChannel(channel, type = ChannelType.PUBNUB_PRIVATE).catch { exception ->
            if (exception.message == CHANNEL_ID_ALREADY_EXIST) {
                Result.success(Unit)
            } else {
                Result.failure(exception)
            }
        }.thenAsync {
            val moderationEvent: PNFuture<PNMemberArrayResult> =
                if (!restriction.ban && !restriction.mute) {
                    pubNub.removeChannelMembers(channel = channel, uuids = listOf(userId))
                        .alsoAsync { _ ->
                            emitEvent(
                                channelId = INTERNAL_USER_MODERATION_CHANNEL_PREFIX + userId,
                                payload = EventContent.Moderation(
                                    channelId = channel,
                                    restriction = RestrictionType.LIFT,
                                    reason = restriction.reason
                                ),
                            )
                        }
                } else {
                    val custom = createCustomObject(
                        mapOf(
                            RESTRICTION_BAN to restriction.ban,
                            RESTRICTION_MUTE to restriction.mute,
                            RESTRICTION_REASON to restriction.reason
                        )
                    )
                    val uuids = listOf(PNMember.Partial(uuidId = userId, custom = custom, null))

                    // if "Enforce referential integrity for memberships" is enabled we need to make sure that user exists in AppContext
                    createUser(id = userId).catch { exception ->
                        if (exception.message == USER_ID_ALREADY_EXIST) {
                            Result.success(Unit)
                        } else {
                            Result.failure(exception)
                        }
                    }.thenAsync {
                        pubNub.setChannelMembers(channel = channel, users = uuids).alsoAsync { _ ->
                            emitEvent(
                                channelId = INTERNAL_USER_MODERATION_CHANNEL_PREFIX + userId,
                                payload = EventContent.Moderation(
                                    channelId = channel,
                                    restriction = if (restriction.ban) {
                                        RestrictionType.BAN
                                    } else {
                                        RestrictionType.MUTE
                                    },
                                    reason = restriction.reason
                                ),
                            )
                        }
                    }
                }
            moderationEvent.then { }
        }
    }

    override fun registerPushChannels(channels: List<String>): PNFuture<PNPushAddChannelResult> {
        return getCommonPushOptions().asFuture().thenAsync { pushOptions ->
            pubNub.addPushNotificationsOnChannels(
                pushType = pushOptions.deviceGateway,
                channels = channels,
                deviceId = pushOptions.deviceToken!!,
                topic = pushOptions.apnsTopic,
                environment = pushOptions.apnsEnvironment
            )
        }
    }

    override fun unregisterPushChannels(channels: List<String>): PNFuture<PNPushRemoveChannelResult> {
        return getCommonPushOptions().asFuture().thenAsync { pushOptions ->
            pubNub.removePushNotificationsFromChannels(
                pushType = pushOptions.deviceGateway,
                channels = channels,
                deviceId = pushOptions.deviceToken!!,
                topic = pushOptions.apnsTopic,
                environment = pushOptions.apnsEnvironment
            )
        }
    }

    override fun unregisterAllPushChannels(): PNFuture<Unit> {
        return getCommonPushOptions().asFuture().thenAsync { pushOption ->
            pubNub.removeAllPushNotificationsFromDeviceWithPushToken(
                pushType = pushOption.deviceGateway,
                deviceId = pushOption.deviceToken!!,
                topic = pushOption.apnsTopic,
                environment = pushOption.apnsEnvironment
            ).then { }
        }
    }

    override fun getThreadChannel(message: Message): PNFuture<ThreadChannel> {
        val threadChannelId = getThreadId(message.channelId, message.timetoken)
        return pubNub.getChannelMetadata(threadChannelId).then {
            ThreadChannelImpl.fromDTO(this, message, it.data)
        }.catch {
            if (it is PubNubException && it.statusCode == HTTP_ERROR_404) {
                Result.failure(PubNubException(THIS_MESSAGE_IS_NOT_A_THREAD, it))
            } else {
                Result.failure(it)
            }
        }
    }

    override fun getUnreadMessagesCounts(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>
    ): PNFuture<List<GetUnreadMessagesCounts>> {
        return fetchUnreadMessagesCounts(limit, page, filter, sort).then {
            it.countsByChannel
        }
    }

    override fun fetchUnreadMessagesCounts(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>
    ): PNFuture<UnreadMessagesCounts> {
        return currentUser.getMemberships(limit = limit, page = page, filter = filter, sort = sort)
            .thenAsync { membershipsResponse: MembershipsResponse ->
                val memberships = membershipsResponse.memberships
                if (memberships.isEmpty()) {
                    return@thenAsync UnreadMessagesCounts(emptyList(), null, null).asFuture()
                }
                val channels = memberships.map { membership -> membership.channel.id }
                val channelsTimetoken = memberships.map { membership -> membership.lastReadMessageTimetoken ?: 0 }

                pubNub.messageCounts(channels = channels, channelsTimetoken = channelsTimetoken)
                    .then { pnMessageCountResult ->
                        val unreadMessageCounts =
                            pnMessageCountResult.channels.map { (channelId, messageCount) ->
                                val membershipMatchingChannel =
                                    memberships.find { membership: Membership -> membership.channel.id == channelId }
                                        ?: log.pnError("$CAN_NOT_FIND_CHANNEL_WITH_ID$channelId")
                                GetUnreadMessagesCounts(
                                    channel = membershipMatchingChannel.channel,
                                    membership = membershipMatchingChannel,
                                    count = messageCount,
                                )
                            }
                        UnreadMessagesCounts(
                            countsByChannel = unreadMessageCounts.filter { unreadMessageCount -> unreadMessageCount.count > 0 },
                            prev = membershipsResponse.prev,
                            next = membershipsResponse.next
                        )
                    }
            }
    }

    override fun markAllMessagesAsRead(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): PNFuture<MarkAllMessageAsReadResponse> {
        return currentUser.getMemberships(limit = limit, page = page, filter = filter, sort = sort)
            .thenAsync { userMembershipsResponse: MembershipsResponse ->
                if (userMembershipsResponse.memberships.isEmpty()) {
                    return@thenAsync MarkAllMessageAsReadResponse(
                        emptyList(),
                        null,
                        null,
                        0,
                        userMembershipsResponse.status
                    ).asFuture()
                }
                val relevantChannelIds: List<String> =
                    userMembershipsResponse.memberships.map { membership -> membership.channel.id }
                pubNub.fetchMessages(channels = relevantChannelIds, page = PNBoundedPage(limit = 1))
                    .thenAsync { lastMessagesFromMembershipChannels: PNFetchMessagesResult ->

                        val channelMembershipInputs = userMembershipsResponse.memberships.map { membership ->
                            val channelId = membership.channel.id
                            val relevantLastMessageTimeToken: Long =
                                getTimetokenFromHistoryMessage(channelId, lastMessagesFromMembershipChannels)

                            val customMap: Map<String, Any?> = buildMap {
                                membership.custom?.let { putAll(it) }
                                // toString is required because server for odd numbers larger than 9007199254740991(timetoken has 17 digits)
                                // returns values that differ by one
                                put(METADATA_LAST_READ_MESSAGE_TIMETOKEN, relevantLastMessageTimeToken.toString())
                            }

                            PNChannelMembership.Partial(
                                channelId = channelId,
                                custom = createCustomObject(customMap)
                            )
                        }.toList()
                        val filterExpression = relevantChannelIds.joinToString(" || ") { "channel.id == '$it'" }

                        pubNub.setMemberships(
                            channels = channelMembershipInputs,
                            filter = filterExpression,
                            userId = currentUser.id,
                            include = MembershipInclude(
                                includeCustom = true,
                                includeStatus = false,
                                includeType = false,
                                includeTotalCount = true,
                                includeChannel = true,
                                includeChannelCustom = true,
                                includeChannelType = true,
                                includeChannelStatus = false
                            ),
                        ).alsoAsync { _: PNChannelMembershipArrayResult ->
                            val emitEventFutures: List<PNFuture<PNPublishResult>> =
                                relevantChannelIds.map { channelId: String ->
                                    val relevantLastMessageTimeToken =
                                        getTimetokenFromHistoryMessage(channelId, lastMessagesFromMembershipChannels)
                                    emitEvent(
                                        channelId = channelId,
                                        payload = EventContent.Receipt(relevantLastMessageTimeToken)
                                    )
                                }
                            emitEventFutures.awaitAll()
                        }.then { setMembershipsResponse: PNChannelMembershipArrayResult ->
                            MarkAllMessageAsReadResponse(
                                memberships = setMembershipsResponse.data.map { membership: PNChannelMembership ->
                                    MembershipImpl.fromMembershipDTO(
                                        this,
                                        membership,
                                        currentUser
                                    )
                                },
                                next = setMembershipsResponse.next,
                                prev = setMembershipsResponse.prev,
                                total = setMembershipsResponse.totalCount ?: 0,
                                status = setMembershipsResponse.status
                            )
                        }
                    }
            }
    }

    override fun getChannelSuggestions(text: String, limit: Int): PNFuture<List<Channel>> {
        val cacheKey: String = text

        suggestedChannelsCache[cacheKey]?.let { nonNullChannels ->
            return nonNullChannels.asFuture()
        }

        return getChannels(filter = "name LIKE '$cacheKey*'", limit = limit).then { getChannelsResponse ->
            suggestedChannelsCache[cacheKey] = getChannelsResponse.channels
            getChannelsResponse.channels
        }
    }

    override fun getUserSuggestions(text: String, limit: Int): PNFuture<List<User>> {
        suggestedUsersCache[text]?.let { nonNullUser ->
            return nonNullUser.asFuture()
        }

        return getUsers(filter = "name LIKE '$text*'", limit = limit).then { getUsersResponse ->
            suggestedUsersCache[text] = getUsersResponse.users
            getUsersResponse.users
        }
    }

    override fun getPushChannels(): PNFuture<List<String>> {
        return getCommonPushOptions().asFuture().thenAsync { pushOptions: PushNotificationsConfig ->
            pubNub.auditPushChannelProvisions(
                pushType = pushOptions.deviceGateway,
                deviceId = pushOptions.deviceToken!!,
                topic = pushOptions.apnsTopic,
                environment = pushOptions.apnsEnvironment
            ).then { pnPushListProvisionsResult: PNPushListProvisionsResult ->
                pnPushListProvisionsResult.channels
            }
        }
    }

    override fun getEventsHistory(
        channelId: String,
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int
    ): PNFuture<GetEventsHistoryResult> {
        return pubNub.fetchMessages(
            channels = listOf(channelId),
            page = PNBoundedPage(startTimetoken, endTimetoken, count),
            includeUUID = true,
            includeMeta = false,
            includeMessageActions = false,
            includeMessageType = true
        ).then { pnFetchMessagesResult: PNFetchMessagesResult ->
            val pnFetchMessageItems: List<PNFetchMessageItem> =
                pnFetchMessagesResult.channelsUrlDecoded[channelId] ?: emptyList()
            val events = pnFetchMessageItems.mapNotNull { pnFetchMessageItem: PNFetchMessageItem ->
                if (pnFetchMessageItem.uuid in mutedUsersManager.mutedUsers) {
                    null
                } else {
                    EventImpl.fromDTO(
                        chat = this,
                        channelId = channelId,
                        pnFetchMessageItem = pnFetchMessageItem
                    )
                }
            }

            GetEventsHistoryResult(events = events, isMore = (count == pnFetchMessageItems.size))
        }
    }

    override fun getCurrentUserMentions(
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int
    ): PNFuture<GetCurrentUserMentionsResult> {
        if (count > 100) {
            return log.logErrorAndReturnException(COUNT_SHOULD_NOT_EXCEED_100).asFuture()
        }
        var isMore = false

        return getEventsHistory(
            channelId = currentUser.id,
            startTimetoken = startTimetoken,
            endTimetoken = endTimetoken,
            count = count
        ).thenAsync { getEventsHistoryResult: GetEventsHistoryResult ->
            isMore = getEventsHistoryResult.isMore
            getEventsHistoryResult.events
                .filter { it.payload is EventContent.Mention } // this performs actual filtering
                .filterIsInstance<Event<EventContent.Mention>>() // this adjusts the type passed into map() below
                .map { mentionEvent: Event<EventContent.Mention> ->
                    val mentionTimetoken = mentionEvent.payload.messageTimetoken
                    val mentionChannelId = mentionEvent.payload.channel

                    BaseChannel.getMessage(chat = this, channelId = mentionChannelId, timetoken = mentionTimetoken)
                        .then { message: Message? ->
                            if (message == null) {
                                return@then null
                            }
                            if (mentionEvent.payload.parentChannel == null) {
                                ChannelMentionData(
                                    event = mentionEvent,
                                    message = message,
                                    userId = mentionEvent.userId,
                                    channelId = mentionChannelId
                                )
                            } else {
                                ThreadMentionData(
                                    event = mentionEvent,
                                    message = message,
                                    userId = mentionEvent.userId,
                                    parentChannelId = mentionEvent.payload.parentChannel.orEmpty(),
                                    threadChannelId = mentionEvent.payload.channel
                                )
                            }
                        }
                }.awaitAll()
        }
            .then { it.filterNotNull() }
            .then { userMentionDataList: List<UserMentionData> ->
                GetCurrentUserMentionsResult(enhancedMentionsData = userMentionDataList, isMore = isMore)
            }
    }

    override fun getChannelGroup(id: String): ChannelGroup {
        return ChannelGroupImpl(id, this)
    }

    override fun removeChannelGroup(id: String): PNFuture<Unit> {
        return pubNub.deleteChannelGroup(id).then { }
    }

    override fun destroy() {
        timerManager.destroy()
        pubNub.destroy()
    }

    override fun getMessageFromReport(
        reportEvent: Event<EventContent.Report>,
        lookupBefore: Duration,
        lookupAfter: Duration
    ): PNFuture<Message?> {
        val report = reportEvent.payload
        val channel = ChannelImpl(this, id = requireNotNull(report.reportedMessageChannelId))

        return when {
            report.reportedMessageTimetoken != null -> {
                channel.getMessage(report.reportedMessageTimetoken!!)
            }

            report.autoModerationId != null -> {
                val reportTimetoken = reportEvent.timetoken
                val maxTimetoken =
                    TimetokenUtil.instantToTimetoken(TimetokenUtil.timetokenToInstant(reportTimetoken) + lookupAfter)
                val minTimetoken =
                    TimetokenUtil.instantToTimetoken(TimetokenUtil.timetokenToInstant(reportTimetoken) - lookupBefore)
                val predicate = { message: Message ->
                    message.meta?.get(METADATA_AUTO_MODERATION_ID) == report.autoModerationId
                }
                // let's try to optimize by first getting messages right after the know timetoken
                channel.getHistory(endTimetoken = reportTimetoken, count = 50).thenAsync { historyResponse ->
                    val result = historyResponse.messages.firstOrNull(predicate)
                    result?.asFuture()
                        // if that fails, let's check the time range
                        ?: findMessageBetween(
                            channel = channel,
                            start = maxTimetoken,
                            end = minTimetoken,
                            lastStart = Long.MAX_VALUE,
                            iterationNumber = 0,
                            match = predicate
                        )
                }
            }

            else -> null.asFuture()
        }
    }

    override fun addConnectionStatusListener(callback: (ConnectionStatus) -> Unit): AutoCloseable {
        val listenerId: String = generateRandomUuid()
        log.d { "Adding connection status listener [ID: $listenerId]" }

        synchronized(connectionStatusListenersLock) {
            val statusListener: StatusListener = createStatusListener(pubNub) { _, status: PNStatus ->
                val logMessageBase =
                    "Received PNStatus: ${status.category} for listener [ID: $listenerId] that resolves to"
                when (status.category) {
                    PNStatusCategory.PNConnectedCategory -> {
                        log.d { "$logMessageBase $PN_CONNECTION_ONLINE" }
                        callback(ConnectionStatus(PN_CONNECTION_ONLINE))
                    }

                    PNStatusCategory.PNSubscriptionChanged -> {
                        // no action in this case
                    }

                    PNStatusCategory.PNUnexpectedDisconnectCategory, PNStatusCategory.PNConnectionError -> {
                        log.d { "$logMessageBase $PN_CONNECTION_ERROR" }
                        callback(
                            ConnectionStatus(
                                PN_CONNECTION_ERROR,
                                status.exception
                            )
                        )
                    }

                    PNStatusCategory.PNDisconnectedCategory -> {
                        log.d { "$logMessageBase $PN_CONNECTION_OFFLINE" }
                        callback(ConnectionStatus(PN_CONNECTION_OFFLINE))
                    }

                    else -> {
                        // Ignore other categories
                    }
                }
            }
            // Copy-on-write: create new map with the added listener
            connectionStatusListenersMap = connectionStatusListenersMap + (listenerId to statusListener)
            pubNub.addListener(statusListener)
        }
        return AutoCloseable {
            removeConnectionStatusListener(listenerId)
        }
    }

    override fun reconnectSubscriptions(): PNFuture<Unit> {
        log.d { "Reconnecting PubNub subscriptions" }
        pubNub.reconnect()
        return Unit.asFuture()
    }

    override fun disconnectSubscriptions(): PNFuture<Unit> {
        log.d { "Disconnecting PubNub subscriptions" }
        pubNub.disconnect()
        return Unit.asFuture()
    }

    private fun removeConnectionStatusListener(listenerId: String) {
        log.d { "Removing connection status listener [ID: $listenerId]" }
        synchronized(connectionStatusListenersLock) {
            connectionStatusListenersMap[listenerId]?.let { statusListener ->
                pubNub.removeListener(statusListener)
                // Copy-on-write: create new map without the removed listener
                connectionStatusListenersMap = connectionStatusListenersMap - listenerId
            }
        }
    }

    internal fun findMessageBetween(
        channel: Channel,
        start: Long,
        end: Long,
        lastStart: Long,
        iterationNumber: Int,
        countPerRequest: Int = 100,
        match: (Message) -> Boolean
    ): PNFuture<Message?> {
        val currentIterationNumber = iterationNumber + 1
        if (start >= lastStart || currentIterationNumber >= 10) {
            // Timetoken hasn't moved or got stuck or 10 iteration occurred(maybe someone doesn't set autoModerationId when creating moderation event)  – stop recursion
            return null.asFuture()
        }

        return channel.getHistory(startTimetoken = start, endTimetoken = end, count = countPerRequest)
            .thenAsync { historyResponse ->
                val result = historyResponse.messages.firstOrNull(match)
                if (result != null) {
                    result.asFuture()
                } else if (historyResponse.messages.isEmpty()) {
                    null.asFuture()
                } else {
                    val newStart = historyResponse.messages.minOf { it.timetoken }
                    findMessageBetween(
                        channel,
                        start = newStart,
                        end = end,
                        lastStart = start,
                        iterationNumber = currentIterationNumber,
                        countPerRequest = countPerRequest,
                        match = match
                    )
                }
            }
    }

    private fun getTimetokenFromHistoryMessage(
        channelId: String,
        pnFetchMessagesResult: PNFetchMessagesResult
    ): Long {
        val relevantLastMessage: List<PNFetchMessageItem>? = pnFetchMessagesResult.channelsUrlDecoded[channelId]
        return relevantLastMessage?.firstOrNull()?.timetoken ?: 0
    }

    private fun getCommonPushOptions(): PushNotificationsConfig {
        if (config.pushNotifications.deviceToken == null) {
            log.pnError(DEVICE_TOKEN_HAS_TO_BE_DEFINED_IN_CHAT_PUSHNOTIFICATIONS_CONFIG)
        }
        return config.pushNotifications
    }

    private fun performSoftUserDelete(user: User): PNFuture<User> {
        val updatedUser = (user as UserImpl).copy(status = DELETED)
        return pubNub.setUUIDMetadata(
            uuid = user.id,
            name = updatedUser.name,
            externalId = updatedUser.externalId,
            profileUrl = updatedUser.profileUrl,
            email = updatedUser.email,
            custom = updatedUser.custom?.let { createCustomObject(it) },
            includeCustom = false,
            type = updatedUser.type,
            status = updatedUser.status,
        ).then { pnUUIDMetadataResult ->
            UserImpl.fromDTO(this, pnUUIDMetadataResult.data)
        }
    }

    private fun performUserDelete(userId: String): PNFuture<Unit> =
        pubNub.removeUUIDMetadata(uuid = userId).then { }

    private fun performSoftChannelDelete(channel: Channel): PNFuture<Channel> {
        val updatedChannel = (channel as BaseChannel<*, *>).copyWithStatusDeleted()
        return pubNub.setChannelMetadata(
            channel = channel.id,
            name = updatedChannel.name,
            description = updatedChannel.description,
            custom = updatedChannel.custom?.let { createCustomObject(it) },
            includeCustom = false,
            type = updatedChannel.type?.stringValue,
            status = updatedChannel.status
        ).then { pnChannelMetadataResult ->
            ChannelImpl.fromDTO(this, pnChannelMetadataResult.data)
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_SOFT_DELETE_CHANNEL, exception))
        }
    }

    private fun performChannelDelete(channelId: String): PNFuture<Unit> =
        pubNub.removeChannelMetadata(channel = channelId).then { }

    private fun setChannelMetadata(
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?,
    ): PNFuture<Channel> {
        return pubNub.setChannelMetadata(
            channel = id,
            name = name,
            description = description,
            custom = custom,
            includeCustom = true,
            type = type?.stringValue,
            status = status
        ).then { pnChannelMetadataResult ->
            ChannelImpl.fromDTO(this, pnChannelMetadataResult.data)
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_CREATE_UPDATE_CHANNEL_DATA, exception))
        }
    }

    private fun setUserMetadata(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        type: String? = null,
        status: String? = null,
    ): PNFuture<User> {
        return pubNub.setUUIDMetadata(
            uuid = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            includeCustom = true,
            type = type,
            status = status
        ).then { pnUUIDMetadataResult ->
            UserImpl.fromDTO(this, pnUUIDMetadataResult.data)
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_CREATE_UPDATE_USER_DATA, exception))
        }
    }

    companion object {
        private val log = Logger.withTag("ChatImpl")

        internal fun pinOrUnpinMessageToChannel(
            pubNub: PubNub,
            message: Message?,
            channel: Channel
        ): PNFuture<PNChannelMetadataResult> {
            val customMetadataToSet = channel.custom?.toMutableMap() ?: mutableMapOf()
            if (message == null) {
                customMetadataToSet.remove(PINNED_MESSAGE_TIMETOKEN)
                customMetadataToSet.remove(PINNED_MESSAGE_CHANNEL_ID)
            } else {
                // toString is required because server for odd numbers larger than 9007199254740991(timetoken has 17 digits)
                // in setChannelMetadata response returns values that differ by one
                customMetadataToSet[PINNED_MESSAGE_TIMETOKEN] = message.timetoken.toString()
                customMetadataToSet[PINNED_MESSAGE_CHANNEL_ID] = message.channelId
            }
            return pubNub.setChannelMetadata(
                channel = channel.id,
                includeCustom = true,
                custom = createCustomObject(customMetadataToSet)
            )
        }

        internal fun getThreadId(channelId: String, messageTimetoken: Long): String {
            return "${MESSAGE_THREAD_ID_PREFIX}_${channelId}_$messageTimetoken"
        }
    }

    private fun storeUserActivityTimestamp(): PNFuture<Unit> {
        lastSavedActivityInterval?.cancel()
        runWithDelayTimer?.cancel()

        return getUser(currentUser.id).thenAsync { user: User? ->
            user?.lastActiveTimestamp?.let { lastActiveTimestamp ->
                val currentTime = Clock.System.now()
                val elapsedTimeSinceLastCheck = (currentTime - Instant.fromEpochMilliseconds(lastActiveTimestamp))

                if (elapsedTimeSinceLastCheck >= config.storeUserActivityInterval) {
                    return@thenAsync runSaveTimestampInterval()
                }

                val remainingTime = config.storeUserActivityInterval - elapsedTimeSinceLastCheck
                runWithDelayTimer = timerManager.runWithDelay(remainingTime) {
                    runSaveTimestampInterval().async {}
                }

                return@thenAsync Unit.asFuture()
            } ?: return@thenAsync runSaveTimestampInterval()
        }
    }

    private fun runSaveTimestampInterval(): PNFuture<Unit> {
        return saveTimeStampFunc().then {
            lastSavedActivityInterval?.cancel()
            lastSavedActivityInterval =
                timerManager.runPeriodically(config.storeUserActivityInterval) {
                    saveTimeStampFunc().async { result: Result<Unit> ->
                        result.onFailure { e ->
                            log.e(throwable = e) { e.message.orEmpty() }
                        }
                    }
                }
        }
    }

    private fun saveTimeStampFunc(): PNFuture<Unit> {
        return currentUser.update { user ->
            this.custom = createCustomObject(
                buildMap {
                    user.custom?.let { putAll(it) }
                    put(LAST_ACTIVE_TIMESTAMP, Clock.System.now().toEpochMilliseconds().toString())
                }
            )
        }.then { updatedUser ->
            currentUser = updatedUser
        }
    }

    private fun KClass<out EventContent>.getEmitMethod(): EmitEventMethod? {
        return when (this) {
            EventContent.Custom::class -> null
            EventContent.Receipt::class, EventContent.Typing::class -> EmitEventMethod.SIGNAL
            else -> EmitEventMethod.PUBLISH
        }
    }

    private fun mapLogLevelFromConfigToKmLogging(): Severity {
        return when (config.logLevel) {
            LogLevel.OFF -> Severity.Assert
            LogLevel.ERROR -> Severity.Error
            LogLevel.WARN -> Severity.Warn
            LogLevel.INFO -> Severity.Info
            LogLevel.DEBUG -> Severity.Debug
            LogLevel.VERBOSE -> Severity.Verbose
        }
    }
}

internal expect fun generateRandomUuid(): String

internal fun isValidId(id: String): Boolean {
    return id.isNotEmpty()
}
