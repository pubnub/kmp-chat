package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.api.models.consumer.objects.PNKey
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
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.channel.BaseChannel
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.channel.GetChannelsResponse
import com.pubnub.kmp.channel.ThreadChannelImpl
import com.pubnub.kmp.error.PubNubErrorMessage.CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL
import com.pubnub.kmp.error.PubNubErrorMessage.CHANNEL_ID_ALREADY_EXIST
import com.pubnub.kmp.error.PubNubErrorMessage.CHANNEL_META_DATA_IS_EMPTY
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_CHANNEL_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_USER_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_FORWARD_MESSAGE
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_GET_CHANNELS
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_GET_USERS
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_CHANNEL_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_IS_PRESENT_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_SOFT_DELETE_CHANNEL
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_UPDATE_USER_METADATA
import com.pubnub.kmp.error.PubNubErrorMessage.USER_ID_ALREADY_EXIST
import com.pubnub.kmp.error.PubNubErrorMessage.USER_NOT_EXIST
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.restrictions.Restriction
import com.pubnub.kmp.restrictions.RestrictionType
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.CreateDirectConversationResult
import com.pubnub.kmp.types.CreateGroupConversationResult
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.MessageActionType
import com.pubnub.kmp.types.getMethodFor
import com.pubnub.kmp.user.GetUsersResponse
import com.pubnub.kmp.utils.cyrb53a
import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@JsExport
interface ChatConfig {
    val pubnubConfig: PNConfiguration
    var uuid: String
    var saveDebugLog: Boolean
    var typingTimeout: Duration
    var rateLimitPerChannel: Any
    val pushNotifications: PushNotificationsConfig
}

class PushNotificationsConfig(
    val sendPushes: Boolean,
    val deviceToken: String?,
    val deviceGateway: PNPushType,
    val apnsTopic: String?,
    val apnsEnvironment: PNPushEnvironment
)

class ChatConfigImpl(override val pubnubConfig: PNConfiguration) : ChatConfig {
    override var uuid: String = pubnubConfig.userId.value
    override var saveDebugLog: Boolean = false
    override var typingTimeout: Duration = 5.seconds //millis
    override var rateLimitPerChannel: Any = mutableMapOf<ChannelType, Int>()
    override val pushNotifications: PushNotificationsConfig = PushNotificationsConfig(
        false, null, PNPushType.FCM, null, PNPushEnvironment.DEVELOPMENT
    )
}

internal const val DELETED = "Deleted"

private const val ID_IS_REQUIRED = "Id is required"
private const val CHANNEL_ID_IS_REQUIRED = "Channel Id is required"
private const val ORIGINAL_PUBLISHER = "originalPublisher"

private const val HTTP_ERROR_404 = 404

internal const val INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_"
private const val MESSAGE_THREAD_ID_PREFIX = "PUBNUB_INTERNAL_THREAD"

class ChatImpl(
    override val config: ChatConfig,
    override val pubNub: PubNub = createPubNub(config.pubnubConfig),
    override val editMessageActionName: String = MessageActionType.EDITED.toString(),
    override val deleteMessageActionName: String = MessageActionType.DELETED.toString(),
) : Chat {
    override var currentUser: User = User(this, config.uuid)
        private set

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
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return getUser(id).thenAsync { user: User? ->
            if (user != null) {
                throw PubNubException(USER_ID_ALREADY_EXIST.message)
            }
            setUserMetadata(id, name, externalId, profileUrl, email, custom, type, status)
        }
    }

    override fun getUser(userId: String): PNFuture<User?> {
        if (!isValidId(userId)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return pubNub.getUUIDMetadata(uuid = userId)
            .then<PNUUIDMetadataResult, User?> { pnUUIDMetadataResult: PNUUIDMetadataResult ->
                pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                    User.fromDTO(this, pnUUIDMetadata)
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
    ): PNFuture<GetUsersResponse> {
        return pubNub.getAllUUIDMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).then { pnUUIDMetadataArrayResult: PNUUIDMetadataArrayResult ->
            val users: MutableSet<User> = pnUUIDMetadataArrayResult.data.map { pnUUIDMetadata ->
                User.fromDTO(this, pnUUIDMetadata)
            }.toMutableSet()
            GetUsersResponse(
                users = users,
                next = pnUUIDMetadataArrayResult.next,
                prev = pnUUIDMetadataArrayResult.prev,
                total = pnUUIDMetadataArrayResult.totalCount ?: 0
            )
        }.catch {
            Result.failure(PubNubException(FAILED_TO_GET_USERS.message, it))
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
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return getUser(id).thenAsync { user ->
            if (user == null) {
                error(USER_NOT_EXIST)
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
                    User.fromDTO(this, data)
                } else {
                    error("PNUUIDMetadata is null.")
                }
            }
        }.catch {
            Result.failure(PubNubException(FAILED_TO_UPDATE_USER_METADATA.message, it))
        }
    }

    override fun deleteUser(id: String, soft: Boolean): PNFuture<User> {
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
            } ?: error(USER_NOT_EXIST)

        }
    }

    override fun wherePresent(userId: String): PNFuture<List<String>> {
        if (!isValidId(userId)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }

        return pubNub.whereNow(uuid = userId).then { pnWhereNowResult ->
            pnWhereNowResult.channels
        }.catch { pnException ->
            Result.Companion.failure(PubNubException(FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA.message, pnException))
        }
    }

    override fun isPresent(userId: String, channel: String): PNFuture<Boolean> {
        if (!isValidId(userId)) {
            return PubNubException(ID_IS_REQUIRED).asFuture()
        }
        if (!isValidId(channel)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }

        return pubNub.whereNow(uuid = userId).then { pnWhereNowResult ->
            pnWhereNowResult.channels.contains(channel)
        }.catch { pnException ->
            Result.failure(PubNubException(FAILED_TO_RETRIEVE_IS_PRESENT_DATA.message, pnException))
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
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return getChannel(id).thenAsync { channel: Channel? ->
            if (channel != null) {
                error(CHANNEL_ID_ALREADY_EXIST)
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
            Result.failure(PubNubException(FAILED_TO_GET_CHANNELS.message, exception))
        }
    }

    override fun getChannel(channelId: String): PNFuture<Channel?> {
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
        custom: CustomObject?,
        description: String?,
        updated: String?,
        status: String?,
        type: ChannelType?
    ): PNFuture<Channel> {
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

    override fun deleteChannel(id: String, soft: Boolean): PNFuture<Channel> {
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

    override fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult> {
        if (!isValidId(channelId)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        if (message.channelId == channelId) {
            return PubNubException(CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL.message).asFuture()
        }

        val meta = message.meta?.toMutableMap() ?: mutableMapOf()
        meta[ORIGINAL_PUBLISHER] = message.userId

        return pubNub.publish(
            message = message.content,
            channel = channelId,
            meta = meta,
            ttl = message.timetoken.toInt()
        ).catch { exception ->
            Result.failure(PubNubException(FAILED_TO_FORWARD_MESSAGE.message, exception))
        }
    }


    override fun <T : EventContent> emitEvent(channel: String, payload: T, mergePayloadWith: Map<String, Any>?): PNFuture<PNPublishResult> {
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
        channelCustom: CustomObject?,
        channelStatus: String?,
        custom: CustomObject?,
    ): PNFuture<CreateDirectConversationResult> {
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
            awaitAll(
                hostMembershipFuture,
                channel.invite(invitedUser)
            ).then { (hostMembershipResponse: PNChannelMembershipArrayResult, inviteeMembership: Membership) ->
                CreateDirectConversationResult(
                    channel,
                    Membership.fromMembershipDTO(this, hostMembershipResponse.data.first(), user),
                    inviteeMembership,
                )
            }
        }
    }

    override fun createGroupConversation(
        invitedUsers: Collection<User>,
        channelId: String,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?,
        custom: CustomObject?
    ): PNFuture<CreateGroupConversationResult> {
        val user = this.currentUser
        return getChannel(channelId).thenAsync { channel ->
            channel?.asFuture() ?: createChannel(
                channelId,
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
            awaitAll(
                hostMembershipFuture,
                channel.inviteMultiple(invitedUsers)
            ).then { (hostMembershipResponse: PNChannelMembershipArrayResult, inviteeMemberships: List<Membership>) ->
                CreateGroupConversationResult(
                    channel,
                    Membership.fromMembershipDTO(this, hostMembershipResponse.data.first(), user),
                    inviteeMemberships.toTypedArray(),
                )
            }
        }
    }

    override fun whoIsPresent(channelId: String): PNFuture<Collection<String>> {
        if (!isValidId(channelId)) {
            return PubNubException(CHANNEL_ID_IS_REQUIRED).asFuture()
        }
        return pubNub.hereNow(listOf(channelId)).then {
            (it.channels[channelId]?.occupants?.map(PNHereNowOccupantData::uuid) ?: emptyList()) as Collection<String>
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA.message, exception))
        }
    }

    internal fun publish(
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean? = null,
        usePost: Boolean = false,
        replicate: Boolean = true,
        ttl: Int? = null,
        mergeMessageWith: Map<String, Any>? = null,
    ): PNFuture<PNPublishResult> {
        val finalMessage = merge(message, mergeMessageWith)
        return pubNub.publish(channelId, finalMessage, meta, shouldStore, usePost, replicate, ttl)
    }

    internal fun signal(
        channelId: String,
        message: EventContent,
        mergeMessageWith: Map<String, Any>? = null,
        ): PNFuture<PNPublishResult> {
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
        val handler = fun(_: PubNub, pnEvent: PNEvent) {
            if (pnEvent.channel != channel) return
            val message = (pnEvent as? MessageResult)?.message ?: return
            val eventContent: EventContent = PNDataEncoder.decode(message)

            @Suppress("UNCHECKED_CAST")
            val payload = eventContent as? T ?: return

            val event = Event(
                chat = this,
                timetoken = pnEvent.timetoken!!, //todo can this even be null?
                payload = payload,
                channelId = pnEvent.channel,
                userId = pnEvent.publisher!! //todo can this even be null?
            )
            callback(event)
        }
        val method = getMethodFor(type) ?: customMethod
        val listener = createEventListener(
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
    ): PNFuture<Unit> {
        val channel: String = INTERNAL_MODERATION_PREFIX + restriction.channelId
        val userId = restriction.userId

        val moderationEvent: PNFuture<PNMemberArrayResult> =
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
        return moderationEvent.then { Unit }
    }

    override fun registerPushChannels(channels: List<String>): PNFuture<PNPushAddChannelResult> {
        return getCommonPushOptions().asFuture().thenAsync { pushOptions ->
            pubNub.addPushNotificationsOnChannels(
                pushOptions.deviceGateway,
                channels,
                pushOptions.deviceToken!!,
                pushOptions.apnsTopic,
                pushOptions.apnsEnvironment
            )
        }
    }

    override fun unregisterPushChannels(channels: List<String>): PNFuture<PNPushRemoveChannelResult> {
        return getCommonPushOptions().asFuture().thenAsync { pushOptions ->
            pubNub.removePushNotificationsFromChannels(
                pushOptions.deviceGateway,
                channels,
                pushOptions.deviceToken!!,
                pushOptions.apnsTopic,
                pushOptions.apnsEnvironment
            )
        }
    }

    override fun getThreadChannel(message: Message): PNFuture<ThreadChannel> {
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

    private fun getCommonPushOptions(): PushNotificationsConfig {
        if (config.pushNotifications.deviceToken == null) {
            throw PubNubException("Device Token has to be defined in Chat pushNotifications config.")
        }
        return config.pushNotifications
    }

    private fun isValidId(id: String): Boolean {
        return id.isNotEmpty()
    }

    private fun getChannelData(id: String): PNFuture<Channel> {
        return pubNub.getChannelMetadata(channel = id, includeCustom = false)
            .then { pnChannelMetadataResult: PNChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                    ChannelImpl.fromDTO(this, pnChannelMetadata)
                } ?: error(CHANNEL_META_DATA_IS_EMPTY)
            }.catch { exception ->
                Result.failure(PubNubException(FAILED_TO_RETRIEVE_CHANNEL_DATA.message, exception))
            }
    }

    private fun performSoftUserDelete(user: User): PNFuture<User> {
        val updatedUser = user.copy(status = DELETED)
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
                User.fromDTO(this, pnUUIDMetadata)
            } ?: error("PNUUIDMetadata is null.")
        }
    }

    private fun performUserDelete(user: User): PNFuture<User> = pubNub.removeUUIDMetadata(uuid = user.id).then { user }
    
    private fun performSoftChannelDelete(channel: Channel): PNFuture<Channel> {
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
            Result.failure(PubNubException(FAILED_TO_SOFT_DELETE_CHANNEL.message, exception))
        }
    }

    private fun performChannelDelete(channel: Channel) =
        pubNub.removeChannelMetadata(channel = channel.id).then { channel }

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
            type = type?.name?.lowercase(),
            status = status
        ).then { pnChannelMetadataResult ->
            pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                ChannelImpl.fromDTO(this, pnChannelMetadata)
            } ?: error("No data available to create Channel")
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_CREATE_UPDATE_CHANNEL_DATA.message, exception))
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
            pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                User.fromDTO(this, pnUUIDMetadata)
            } ?: error("No data available to create User")
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_CREATE_UPDATE_USER_DATA.message, exception))
        }
    }

    companion object {
        internal fun pinMessageToChannel(pubNub: PubNub, message: Message?, channel: Channel): PNFuture<PNChannelMetadataResult> {
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
            return "${MESSAGE_THREAD_ID_PREFIX}_${channelId}_${messageTimetoken}"
        }

        internal fun createThreadChannel(chat: ChatImpl, message: Message): PNFuture<ThreadChannel> {
            if (message.channelId.startsWith(MESSAGE_THREAD_ID_PREFIX)) {
                return PubNubException("Only one level of thread nesting is allowed").asFuture()
            }
            if (message.deleted) {
                return PubNubException("You cannot create threads on deleted messages").asFuture()
            }

            val threadChannelId = getThreadId(message.channelId, message.timetoken)
            return chat.getChannel(threadChannelId).thenAsync { it: Channel? ->
                if (it != null) {
                    return@thenAsync PubNubException("Thread for this message already exists").asFuture()
                }
                ThreadChannelImpl(message, chat, description = "Thread on channel ${message.channelId} with message timetoken ${message.timetoken}",
                    id = threadChannelId, threadCreated = false).asFuture()
            }
        }

        internal fun removeThreadChannel(chat: Chat, message: Message, soft: Boolean = false): PNFuture<Pair<PNRemoveMessageActionResult, Channel>> {
            if (!message.hasThread) {
                return PubNubException("There is no thread to be deleted").asFuture()
            }

            val threadId = getThreadId(message.channelId, message.timetoken)

            val actionTimetoken =
                message.actions?.get("threadRootId")?.get(threadId)?.get(0)?.actionTimetoken?.toLongOrNull()
                    ?: return PubNubException("There is no action timetoken corresponding to the thread").asFuture()

            return chat.getChannel(threadId).thenAsync { threadChannel ->
                if (threadChannel == null) {
                    throw PubNubException("There is no thread with id: $threadId")
                }
                awaitAll(
                    chat.pubNub.removeMessageAction(message.channelId, message.timetoken, actionTimetoken),
                    threadChannel.delete(soft)
                )
            }
        }
    }
}
