package com.pubnub.chat.internal

import com.benasher44.uuid.uuid4
import com.pubnub.api.PubNubException
import com.pubnub.api.enums.PNPushType
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.models.consumer.pubsub.MessageResult
import com.pubnub.api.models.consumer.pubsub.PNEvent
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushListProvisionsResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.Membership
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.internal.error.PubNubErrorMessage.CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL
import com.pubnub.chat.internal.error.PubNubErrorMessage.CHANNEL_META_DATA_IS_EMPTY
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_CHANNEL_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_USER_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_FORWARD_MESSAGE
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_CHANNEL_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_SOFT_DELETE_CHANNEL
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.internal.timer.PlatformTimer
import com.pubnub.chat.internal.timer.PlatformTimer.Companion.runPeriodically
import com.pubnub.chat.internal.timer.PlatformTimer.Companion.runWithDelay
import com.pubnub.chat.internal.util.getPhraseToLookFor
import com.pubnub.chat.internal.utils.cyrb53a
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.message.MarkAllMessageAsReadResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.restrictions.RestrictionType
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.CreateGroupConversationResult
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.chat.types.getMethodFor
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.catch
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class ChatImpl(
    override val config: ChatConfiguration,
    override val pubNub: com.pubnub.kmp.PubNub,
    override val editMessageActionName: String = com.pubnub.chat.types.MessageActionType.EDITED.toString(),
    override val deleteMessageActionName: String = com.pubnub.chat.types.MessageActionType.DELETED.toString(),
) : Chat {
    override var currentUser: User =
        UserImpl(this, pubNub.configuration.userId.value, name = pubNub.configuration.userId.value)
        private set

    private val suggestedChannelsCache: MutableMap<String, Set<Channel>> = mutableMapOf()
    private val suggestedUsersCache: MutableMap<String, Set<User>> = mutableMapOf()
    private var lastSavedActivityInterval: PlatformTimer? = null
    private var runWithDelayTimer: PlatformTimer? = null

    fun initialize(): com.pubnub.kmp.PNFuture<Chat> {
        // todo move this to config initialization?
        if (config.storeUserActivityInterval < 60.seconds) {
            throw PubNubException(PubNubErrorMessage.STORE_USER_ACTIVITY_INTERVAL_SHOULD_BE_AT_LEAST_1_MIN)
        }

        if (config.pushNotifications.deviceGateway == PNPushType.APNS2 && config.pushNotifications.apnsTopic == null) {
            throw PubNubException(PubNubErrorMessage.APNS_TOPIC_SHOULD_BE_DEFINED_WHEN_DEVICE_GATEWAY_IS_SET_TO_APNS2)
        }

        return getUser(pubNub.configuration.userId.value).thenAsync { user ->
            user?.asFuture() ?: createUser(currentUser)
        }.then { user ->
            currentUser = user
        }.thenAsync { _: Unit ->
            if (config.storeUserActivityTimestamps) {
                storeUserActivityTimestamp()
            } else {
                Unit.asFuture()
            }
        }.then {
            this
        }
    }

    override fun createUser(user: User): com.pubnub.kmp.PNFuture<User> = createUser(
        id = user.id,
        name = user.name,
        externalId = user.externalId,
        profileUrl = user.profileUrl,
        email = user.email,
        custom = user.custom?.let { createCustomObject(it) },
        status = user.status,
        type = user.type
    )

    // todo
    override fun createUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: com.pubnub.kmp.CustomObject?,
        status: String?,
        type: String?,
    ): com.pubnub.kmp.PNFuture<User> {
        if (!isValidId(id)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return getUser(id).thenAsync { user: User? ->
            if (user != null) {
                throw PubNubException(PubNubErrorMessage.USER_ID_ALREADY_EXIST)
            }
            setUserMetadata(id, name, externalId, profileUrl, email, custom, type, status)
        }
    }

    override fun getUser(userId: String): com.pubnub.kmp.PNFuture<User?> {
        if (!isValidId(userId)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return pubNub.getUUIDMetadata(uuid = userId, includeCustom = true)
            .then<PNUUIDMetadataResult, User?> { pnUUIDMetadataResult: PNUUIDMetadataResult ->
                pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                    UserImpl.fromDTO(this, pnUUIDMetadata)
                } ?: throw PubNubException("PNUUIDMetadataResult is null")
            }.catch {
                if (it is PubNubException && it.statusCode == HTTP_ERROR_404) {
                    Result.success(null)
                } else {
                    Result.failure(it)
                }
            }
    }

    override fun getUsers(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
    ): com.pubnub.kmp.PNFuture<GetUsersResponse> {
        return pubNub.getAllUUIDMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).then { pnUUIDMetadataArrayResult: PNUUIDMetadataArrayResult ->
            val users: MutableSet<User> = pnUUIDMetadataArrayResult.data.map { pnUUIDMetadata ->
                UserImpl.fromDTO(this, pnUUIDMetadata)
            }.toMutableSet()
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
        custom: com.pubnub.kmp.CustomObject?,
        status: String?,
        type: String?
    ): com.pubnub.kmp.PNFuture<User> {
        if (!isValidId(id)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return getUser(id).thenAsync { user ->
            if (user == null) {
                error(PubNubErrorMessage.USER_NOT_EXIST)
            }
            pubNub.setUUIDMetadata(
                uuid = id,
                name = name,
                externalId = externalId,
                profileUrl = profileUrl,
                email = email,
                custom = custom,
                includeCustom = true,
                status = status,
                type = type,
            ).then { result: PNUUIDMetadataResult ->
                val data = result.data
                if (data != null) {
                    UserImpl.fromDTO(this, data)
                } else {
                    error("PNUUIDMetadata is null.")
                }
            }
        }.catch {
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_UPDATE_USER_METADATA, it))
        }
    }

    override fun deleteUser(id: String, soft: Boolean): com.pubnub.kmp.PNFuture<User> {
        if (!isValidId(id)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return getUser(id).thenAsync { user: User? ->
            user?.let { notNullUser ->
                if (soft) {
                    performSoftUserDelete(notNullUser)
                } else {
                    performUserDelete(notNullUser)
                }
            } ?: error(PubNubErrorMessage.USER_NOT_EXIST)
        }
    }

    override fun wherePresent(userId: String): com.pubnub.kmp.PNFuture<List<String>> {
        if (!isValidId(userId)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return pubNub.whereNow(uuid = userId).then { pnWhereNowResult ->
            pnWhereNowResult.channels
        }.catch { pnException ->
            Result.Companion.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA, pnException))
        }
    }

    override fun isPresent(userId: String, channel: String): com.pubnub.kmp.PNFuture<Boolean> {
        if (!isValidId(userId)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }
        if (!isValidId(channel)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }

        return pubNub.whereNow(uuid = userId).then { pnWhereNowResult ->
            pnWhereNowResult.channels.contains(channel)
        }.catch { pnException ->
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_IS_PRESENT_DATA, pnException))
        }
    }

    override fun createChannel(
        id: String,
        name: String?,
        description: String?,
        custom: com.pubnub.kmp.CustomObject?,
        type: ChannelType?,
        status: String?
    ): com.pubnub.kmp.PNFuture<Channel> {
        if (!isValidId(id)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return getChannel(id).thenAsync { channel: Channel? ->
            if (channel != null) {
                error(PubNubErrorMessage.CHANNEL_ID_ALREADY_EXIST)
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
    ): com.pubnub.kmp.PNFuture<GetChannelsResponse> {
        return pubNub.getAllChannelMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).then { pnChannelMetadataArrayResult ->
            val channels: MutableSet<Channel> = pnChannelMetadataArrayResult.data.map { pnChannelMetadata ->
                ChannelImpl.fromDTO(this, pnChannelMetadata)
            }.toMutableSet()
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

    override fun getChannel(channelId: String): com.pubnub.kmp.PNFuture<Channel?> {
        if (!isValidId(channelId)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return pubNub.getChannelMetadata(channel = channelId)
            .then<PNChannelMetadataResult, Channel?> { pnChannelMetadataResult: PNChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata: PNChannelMetadata ->
                    ChannelImpl.fromDTO(this, pnChannelMetadata)
                } ?: error("PNChannelMetadata is null")
            }.catch { exception ->
                if (exception is PubNubException && exception.statusCode == HTTP_ERROR_404) {
                    Result.success(null)
                } else {
                    Result.failure(exception)
                }
            }
    }

    override fun updateChannel(
        id: String,
        name: String?,
        custom: com.pubnub.kmp.CustomObject?,
        description: String?,
        updated: String?,
        status: String?,
        type: ChannelType?
    ): com.pubnub.kmp.PNFuture<Channel> {
        if (!isValidId(id)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }

        return getChannel(id).thenAsync { channel: Channel? ->
            if (channel != null) {
                setChannelMetadata(id, name, description, custom, type, status)
            } else {
                error("Channel not found")
            }
        }
    }

    override fun deleteChannel(id: String, soft: Boolean): com.pubnub.kmp.PNFuture<Channel> {
        if (!isValidId(id)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }

        return getChannelData(id).thenAsync { channel: Channel ->
            if (soft) {
                performSoftChannelDelete(channel)
            } else {
                performChannelDelete(channel)
            }
        }
    }

    override fun forwardMessage(message: com.pubnub.chat.Message, channelId: String): com.pubnub.kmp.PNFuture<PNPublishResult> {
        if (!isValidId(channelId)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        if (message.channelId == channelId) {
            return PubNubException(CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL).asFuture()
        }

        val meta = message.meta?.toMutableMap() ?: mutableMapOf()
        meta[ORIGINAL_PUBLISHER] = message.userId

        return pubNub.publish(
            message = message.content,
            channel = channelId,
            meta = meta,
            ttl = message.timetoken.toInt()
        ).catch { exception ->
            Result.failure(PubNubException(FAILED_TO_FORWARD_MESSAGE, exception))
        }
    }

    override fun <T : EventContent> emitEvent(
        channel: String,
        payload: T,
        mergePayloadWith: Map<String, Any>?
    ): com.pubnub.kmp.PNFuture<PNPublishResult> {
        return if (getMethodFor(payload::class) == EmitEventMethod.SIGNAL) {
            signal(channelId = channel, message = payload, mergeMessageWith = mergePayloadWith)
        } else {
            publish(channelId = channel, message = payload, mergeMessageWith = mergePayloadWith)
        }
    }

    override fun createDirectConversation(
        invitedUser: User,
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: com.pubnub.kmp.CustomObject?,
        channelStatus: String?,
        custom: com.pubnub.kmp.CustomObject?,
    ): com.pubnub.kmp.PNFuture<com.pubnub.chat.types.CreateDirectConversationResult> {
        val user = this.currentUser
        val sortedUsers = listOf(invitedUser.id, user.id).sorted()
        val finalChannelId = channelId ?: "direct${cyrb53a("${sortedUsers[0]}&${sortedUsers[1]}")}"

        return getChannel(finalChannelId).thenAsync { channel ->
            channel?.asFuture() ?: createChannel(
                finalChannelId,
                channelName,
                channelDescription,
                channelCustom,
                ChannelType.DIRECT,
                channelStatus
            )
        }.thenAsync { channel: Channel ->
            val hostMembershipFuture = pubNub.setMemberships(
                listOf(PNChannelMembership.Partial(channel.id, custom)),
                filter = "channel.id == '${channel.id}'",
                includeCustom = true,
                includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
                includeCount = true,
                includeType = true,
            )
            com.pubnub.kmp.awaitAll(
                hostMembershipFuture,
                channel.invite(invitedUser)
            ).then { (hostMembershipResponse: PNChannelMembershipArrayResult, inviteeMembership: Membership) ->
                com.pubnub.chat.types.CreateDirectConversationResult(
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
        channelCustom: com.pubnub.kmp.CustomObject?,
        channelStatus: String?,
        custom: com.pubnub.kmp.CustomObject?
    ): com.pubnub.kmp.PNFuture<CreateGroupConversationResult> {
        val user = this.currentUser
        val finalChannelId = channelId ?: uuid4().toString()
        return getChannel(finalChannelId).thenAsync { channel ->
            channel?.asFuture() ?: createChannel(
                finalChannelId,
                channelName,
                channelDescription,
                channelCustom,
                ChannelType.DIRECT,
                channelStatus
            )
        }.thenAsync { channel: Channel ->
            val hostMembershipFuture = pubNub.setMemberships(
                listOf(PNChannelMembership.Partial(channel.id, custom)),
                filter = "channel.id == '${channel.id}'",
                includeCustom = true,
                includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
                includeCount = true,
                includeType = true,
            )
            com.pubnub.kmp.awaitAll(
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

    override fun whoIsPresent(channelId: String): com.pubnub.kmp.PNFuture<Collection<String>> {
        if (!isValidId(channelId)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return pubNub.hereNow(listOf(channelId)).then {
            (it.channels[channelId]?.occupants?.map(PNHereNowOccupantData::uuid) ?: emptyList())
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA, exception))
        }
    }

    override fun publish(
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?,
        mergeMessageWith: Map<String, Any>?,
    ): com.pubnub.kmp.PNFuture<PNPublishResult> {
        val finalMessage = merge(message, mergeMessageWith)
        return pubNub.publish(channelId, finalMessage, meta, shouldStore, usePost, replicate, ttl)
    }

    override fun signal(
        channelId: String,
        message: EventContent,
        mergeMessageWith: Map<String, Any>?,
    ): com.pubnub.kmp.PNFuture<PNPublishResult> {
        val finalMessage = merge(message, mergeMessageWith)
        return pubNub.signal(channelId, finalMessage)
    }

    private fun merge(
        message: EventContent,
        mergeMessageWith: Map<String, Any>?,
    ): Map<String, Any> {
        var finalMessage = PNDataEncoder.encode(message) as Map<String, Any>
        if (mergeMessageWith != null) {
            finalMessage = buildMap {
                putAll(finalMessage)
                putAll(mergeMessageWith)
            }
        }
        return finalMessage
    }

    override fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channel: String,
        customMethod: EmitEventMethod?,
        callback: (event: Event<T>) -> Unit
    ): AutoCloseable {
        val handler = fun(_: com.pubnub.kmp.PubNub, pnEvent: PNEvent) {
            if (pnEvent.channel != channel) return
            val message = (pnEvent as? MessageResult)?.message ?: return
            val eventContent: EventContent = PNDataEncoder.decode(message)

            @Suppress("UNCHECKED_CAST")
            val payload = eventContent as? T ?: return

            val event = Event(
                chat = this,
                timetoken = pnEvent.timetoken!!, // todo can this even be null?
                payload = payload,
                channelId = pnEvent.channel,
                userId = pnEvent.publisher!! // todo can this even be null?
            )
            callback(event)
        }
        val method = getMethodFor(type) ?: customMethod
        val listener = com.pubnub.kmp.createEventListener(
            pubNub,
            onMessage = if (method == EmitEventMethod.PUBLISH) handler else { _, _ -> },
            onSignal = if (method == EmitEventMethod.SIGNAL) handler else { _, _ -> },
        )
        val channelEntity = pubNub.channel(channel)
        val subscription = channelEntity.subscription()
        subscription.addListener(listener)
        subscription.subscribe()
        return subscription
    }

    override fun setRestrictions(
        restriction: Restriction
    ): com.pubnub.kmp.PNFuture<Unit> {
        val channel: String = INTERNAL_MODERATION_PREFIX + restriction.channelId
        val userId = restriction.userId

        val moderationEvent: com.pubnub.kmp.PNFuture<PNMemberArrayResult> =
            if (!restriction.ban && !restriction.mute) {
                pubNub.removeChannelMembers(channel = channel, uuids = listOf(userId))
                    .alsoAsync { _ ->
                        emitEvent(
                            channel = userId,
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
                        "ban" to restriction.ban,
                        "mute" to restriction.mute,
                        "reason" to restriction.reason
                    )
                )
                val uuids = listOf(PNMember.Partial(uuidId = userId, custom = custom, null))
                pubNub.setChannelMembers(channel = channel, uuids = uuids)
                    .alsoAsync { _ ->
                        emitEvent(
                            channel = userId,
                            payload = EventContent.Moderation(
                                channelId = channel,
                                restriction = if (restriction.ban) RestrictionType.BAN else RestrictionType.MUTE,
                                reason = restriction.reason
                            ),
                        )
                    }
            }
        return moderationEvent.then { }
    }

    override fun registerPushChannels(channels: List<String>): com.pubnub.kmp.PNFuture<PNPushAddChannelResult> {
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

    override fun unregisterPushChannels(channels: List<String>): com.pubnub.kmp.PNFuture<PNPushRemoveChannelResult> {
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

    override fun unregisterAllPushChannels(): com.pubnub.kmp.PNFuture<Unit> {
        return getCommonPushOptions().asFuture().thenAsync { pushOption ->
            pubNub.removeAllPushNotificationsFromDeviceWithPushToken(
                pushType = pushOption.deviceGateway,
                deviceId = pushOption.deviceToken!!,
                topic = pushOption.apnsTopic,
                environment = pushOption.apnsEnvironment
            ).then { }
        }
    }

    override fun getThreadChannel(message: com.pubnub.chat.Message): com.pubnub.kmp.PNFuture<ThreadChannel> {
        val threadChannelId = getThreadId(message.channelId, message.timetoken)
        return pubNub.getChannelMetadata(threadChannelId).then {
            ThreadChannelImpl.fromDTO(this, message, it.data!!)
        }.catch {
            if (it is PubNubException && it.statusCode == HTTP_ERROR_404) {
                Result.failure(PubNubException("This message is not a thread", it))
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
    ): com.pubnub.kmp.PNFuture<Set<GetUnreadMessagesCounts>> {
        return currentUser.getMemberships(limit = limit, page = page, filter = filter, sort = sort)
            .thenAsync { membershipsResponse: MembershipsResponse ->
                val memberships = membershipsResponse.memberships
                if (memberships.isEmpty()) {
                    return@thenAsync emptySet<GetUnreadMessagesCounts>().asFuture()
                }
                val channels = memberships.map { membership -> membership.channel.id }
                val channelsTimetoken =
                    memberships.map { membership -> membership.lastReadMessageTimetoken ?: 0 }
                pubNub.messageCounts(channels = channels, channelsTimetoken = channelsTimetoken)
                    .then { pnMessageCountResult ->
                        val unreadMessageCounts =
                            pnMessageCountResult.channels.map { (channelId, messageCount) ->
                                val membershipMatchingChannel =
                                    memberships.find { membership: Membership -> membership.channel.id == channelId }
                                        ?: throw PubNubException("Cannot find channel with id $channelId")
                                GetUnreadMessagesCounts(
                                    channel = membershipMatchingChannel.channel,
                                    membership = membershipMatchingChannel,
                                    count = messageCount
                                )
                            }
                        unreadMessageCounts.filter { unreadMessageCount -> unreadMessageCount.count > 0 }.toSet()
                    }
            }
    }

    override fun markAllMessagesAsRead(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
    ): com.pubnub.kmp.PNFuture<MarkAllMessageAsReadResponse> {
        return currentUser.getMemberships(limit = limit, page = page, filter = filter, sort = sort)
            .thenAsync { userMembershipsResponse: MembershipsResponse ->
                if (userMembershipsResponse.memberships.isEmpty()) {
                    return@thenAsync MarkAllMessageAsReadResponse(
                        emptySet(),
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
                                put("lastReadMessageTimetoken", relevantLastMessageTimeToken)
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
                            uuid = currentUser.id,
                            includeCount = true,
                            includeCustom = true,
                            includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
                            includeType = true
                        ).alsoAsync { _: PNChannelMembershipArrayResult ->
                            val emitEventFutures: List<com.pubnub.kmp.PNFuture<PNPublishResult>> =
                                relevantChannelIds.map { channelId: String ->
                                    val relevantLastMessageTimeToken =
                                        getTimetokenFromHistoryMessage(channelId, lastMessagesFromMembershipChannels)
                                    emitEvent(
                                        channel = channelId,
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
                                }.toSet(),
                                next = setMembershipsResponse.next,
                                prev = setMembershipsResponse.prev,
                                total = setMembershipsResponse.totalCount ?: 0,
                                status = setMembershipsResponse.status
                            )
                        }
                    }
            }
    }

    override fun getChannelSuggestions(text: String, limit: Int): com.pubnub.kmp.PNFuture<Set<Channel>> {
        val cacheKey: String = getPhraseToLookFor(text, "#") ?: return emptySet<Channel>().asFuture()

        suggestedChannelsCache[cacheKey]?.let { nonNullChannels ->
            return nonNullChannels.asFuture()
        }

        return getChannels(filter = "name LIKE '$cacheKey*'", limit = limit).then { getChannelsResponse ->
            val channels: Set<Channel> = getChannelsResponse.channels
            suggestedChannelsCache[cacheKey] = channels
            channels
        }
    }

    override fun getUserSuggestions(text: String, limit: Int): com.pubnub.kmp.PNFuture<Set<User>> {
        val cacheKey: String = getPhraseToLookFor(text, "@") ?: return emptySet<User>().asFuture()

        suggestedUsersCache[cacheKey]?.let { nonNullUser ->
            return nonNullUser.asFuture()
        }

        return getUsers(filter = "name LIKE '$cacheKey*'", limit = limit).then { getUsersResponse ->
            val users: Set<User> = getUsersResponse.users
            suggestedUsersCache[cacheKey] = users
            users
        }
    }

    override fun getPushChannels(): com.pubnub.kmp.PNFuture<List<String>> {
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

    private fun getTimetokenFromHistoryMessage(channelId: String, pnFetchMessagesResult: PNFetchMessagesResult): Long {
        // todo in TS there is encodeURIComponent(channelId) do we need this?
        val relevantLastMessage: List<PNFetchMessageItem>? = pnFetchMessagesResult.channels[channelId]
        return relevantLastMessage?.firstOrNull()?.timetoken ?: 0
    }

    private fun getCommonPushOptions(): PushNotificationsConfig {
        if (config.pushNotifications.deviceToken == null) {
            throw PubNubException("Device Token has to be defined in Chat pushNotifications config.")
        }
        return config.pushNotifications
    }

    private fun isValidId(id: String): Boolean {
        return id.isNotEmpty()
    }

    private fun getChannelData(id: String): com.pubnub.kmp.PNFuture<Channel> {
        return pubNub.getChannelMetadata(channel = id, includeCustom = false)
            .then { pnChannelMetadataResult: PNChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                    ChannelImpl.fromDTO(this, pnChannelMetadata)
                } ?: error(CHANNEL_META_DATA_IS_EMPTY)
            }.catch { exception ->
                Result.failure(PubNubException(FAILED_TO_RETRIEVE_CHANNEL_DATA, exception))
            }
    }

    private fun performSoftUserDelete(user: User): com.pubnub.kmp.PNFuture<User> {
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
            pnUUIDMetadataResult.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                UserImpl.fromDTO(this, pnUUIDMetadata)
            } ?: error("PNUUIDMetadata is null.")
        }
    }

    private fun performUserDelete(user: User): com.pubnub.kmp.PNFuture<User> = pubNub.removeUUIDMetadata(uuid = user.id).then { user }

    private fun performSoftChannelDelete(channel: Channel): com.pubnub.kmp.PNFuture<Channel> {
        val updatedChannel = (channel as BaseChannel<*, *>).copyWithStatusDeleted()
        return pubNub.setChannelMetadata(
            channel = channel.id,
            name = updatedChannel.name,
            description = updatedChannel.description,
            custom = updatedChannel.custom?.let { createCustomObject(it) },
            includeCustom = false,
            type = updatedChannel.type.toString().lowercase(),
            status = updatedChannel.status
        ).then { pnChannelMetadataResult ->
            pnChannelMetadataResult.data?.let { pnChannelMetadata: PNChannelMetadata ->
                ChannelImpl.fromDTO(this, pnChannelMetadata)
            } ?: error("PNChannelMetadata is null.")
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_SOFT_DELETE_CHANNEL, exception))
        }
    }

    private fun performChannelDelete(channel: Channel) =
        pubNub.removeChannelMetadata(channel = channel.id).then { channel }

    private fun setChannelMetadata(
        id: String,
        name: String?,
        description: String?,
        custom: com.pubnub.kmp.CustomObject?,
        type: ChannelType?,
        status: String?,
    ): com.pubnub.kmp.PNFuture<Channel> {
        return pubNub.setChannelMetadata(
            channel = id,
            name = name,
            description = description,
            custom = custom,
            includeCustom = true,
            type = type?.name?.lowercase(),
            status = status
        ).then { pnChannelMetadataResult ->
            pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                ChannelImpl.fromDTO(this, pnChannelMetadata)
            } ?: error("No data available to create Channel")
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
        custom: com.pubnub.kmp.CustomObject?,
        type: String? = null,
        status: String? = null,
    ): com.pubnub.kmp.PNFuture<User> {
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
            pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                UserImpl.fromDTO(this, pnUUIDMetadata)
            } ?: error("No data available to create User")
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_CREATE_UPDATE_USER_DATA, exception))
        }
    }

    companion object {
        internal fun pinMessageToChannel(
            pubNub: com.pubnub.kmp.PubNub,
            message: com.pubnub.chat.Message?,
            channel: Channel
        ): com.pubnub.kmp.PNFuture<PNChannelMetadataResult> {
            val customMetadataToSet = channel.custom?.toMutableMap() ?: mutableMapOf()
            if (message == null) {
                customMetadataToSet.remove("pinnedMessageTimetoken")
                customMetadataToSet.remove("pinnedMessageChannelID")
            } else {
                customMetadataToSet["pinnedMessageTimetoken"] = message.timetoken
                customMetadataToSet["pinnedMessageChannelID"] = message.channelId
            }
            return pubNub.setChannelMetadata(channel.id, custom = createCustomObject(customMetadataToSet))
        }

        internal fun getThreadId(channelId: String, messageTimetoken: Long): String {
            return "${MESSAGE_THREAD_ID_PREFIX}_${channelId}_$messageTimetoken"
        }

        internal fun createThreadChannel(chat: Chat, message: com.pubnub.chat.Message): com.pubnub.kmp.PNFuture<ThreadChannel> {
            if (message.channelId.startsWith(MESSAGE_THREAD_ID_PREFIX)) {
                return PubNubException("Only one level of thread nesting is allowed").asFuture()
            }
            if (message.deleted) {
                return PubNubException("You cannot create threads on deleted messages").asFuture()
            }

            val threadChannelId =
                getThreadId(message.channelId, message.timetoken)
            return chat.getChannel(threadChannelId).thenAsync { it: Channel? ->
                if (it != null) {
                    return@thenAsync PubNubException("Thread for this message already exists").asFuture()
                }
                ThreadChannelImpl(
                    message,
                    chat,
                    description = "Thread on channel ${message.channelId} with message timetoken ${message.timetoken}",
                    id = threadChannelId,
                    threadCreated = false
                ).asFuture()
            }
        }

        internal fun removeThreadChannel(
            chat: Chat,
            message: com.pubnub.chat.Message,
            soft: Boolean = false
        ): com.pubnub.kmp.PNFuture<Pair<PNRemoveMessageActionResult, Channel>> {
            if (!message.hasThread) {
                return PubNubException("There is no thread to be deleted").asFuture()
            }

            val threadId = getThreadId(message.channelId, message.timetoken)

            val actionTimetoken =
                message.actions?.get("threadRootId")?.get(threadId)?.get(0)?.actionTimetoken
                    ?: return PubNubException("There is no action timetoken corresponding to the thread").asFuture()

            return chat.getChannel(threadId).thenAsync { threadChannel ->
                if (threadChannel == null) {
                    throw PubNubException("There is no thread with id: $threadId")
                }
                com.pubnub.kmp.awaitAll(
                    chat.pubNub.removeMessageAction(message.channelId, message.timetoken, actionTimetoken),
                    threadChannel.delete(soft)
                )
            }
        }
    }

    private fun storeUserActivityTimestamp(): com.pubnub.kmp.PNFuture<Unit> {
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
                runWithDelayTimer = runWithDelay(remainingTime) {
                    runSaveTimestampInterval()
                }

                return@thenAsync runWithDelayTimer.asFuture().then {}
            } ?: return@thenAsync runSaveTimestampInterval()
        }
    }

    private fun runSaveTimestampInterval(): com.pubnub.kmp.PNFuture<Unit> {
        return saveTimeStampFunc().then {
            lastSavedActivityInterval?.cancel()
            lastSavedActivityInterval =
                runPeriodically(config.storeUserActivityInterval) {
                    saveTimeStampFunc().async { result: Result<Unit> ->
                        result.onFailure { e ->
                            // todo log e "error setting lastActiveTimestamp"
                        }
                    }
                }
        }
    }

    private fun saveTimeStampFunc(): com.pubnub.kmp.PNFuture<Unit> {
        val customWithUpdatedLastActiveTimestamp = buildMap {
            currentUser.custom?.let { putAll(it) }
            put("lastActiveTimestamp", Clock.System.now().toEpochMilliseconds())
        }
        return pubNub.setUUIDMetadata(
            uuid = currentUser.id,
            custom = createCustomObject(customWithUpdatedLastActiveTimestamp),
            includeCustom = true,
        ).then { pnUUIDMetadataResult: PNUUIDMetadataResult ->
            if (pnUUIDMetadataResult.data != null) {
                currentUser = UserImpl.fromDTO(this, pnUUIDMetadataResult.data!!)
            } else {
                error("PNUUIDMetadata is null.")
            }
        }
    }
}
