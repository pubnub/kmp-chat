package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.channel.GetChannelsResponse
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
import com.pubnub.kmp.types.CreateDirectConversationResult
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.user.GetUsersResponse
import kotlin.js.JsExport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@JsExport
interface ChatConfig {
    val pubnubConfig: PNConfiguration
    var uuid: String
    var saveDebugLog: Boolean
    var typingTimeout: Duration
    var rateLimitPerChannel: Any
}

class ChatConfigImpl(override val pubnubConfig: PNConfiguration) : ChatConfig {
    override var uuid: String = ""
    override var saveDebugLog: Boolean = false
    override var typingTimeout: Duration = 5.seconds //millis
    override var rateLimitPerChannel: Any = mutableMapOf<ChannelType, Int>()
}

private const val DELETED = "Deleted"

private const val ID_IS_REQUIRED = "Id is required"
private const val CHANNEL_ID_IS_REQUIRED = "Channel Id is required"
private const val ORIGINAL_PUBLISHER = "originalPublisher"

private const val HTTP_ERROR_404 = 404

class ChatImpl(
    override val config: ChatConfig,
    override val pubNub: PubNub = createPubNub(config.pubnubConfig)
) : Chat {
    private val user: User? = null

//    override suspend fun createUser(
//        id: String,
//        name: String?,
//        externalId: String?,
//        profileUrl: String?,
//        email: String?,
//        custom: CustomObject?,
//        status: String?,
//        type: String?
//    ) = suspendCancellableCoroutine { cont ->
//        createUser(id, name, externalId, profileUrl, email, custom, status, type) {
//            it.onSuccess {
//                cont.resume(it)
//            }.onFailure {
//                cont.resumeWithException(it)
//            }
//        }
//    }

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
                    createUserFromMetadata(this, pnUUIDMetadata)
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
                createUserFromMetadata(this, pnUUIDMetadata)
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
                includeCustom = false,
                status = status,
                type = type,
            ).then { result: PNUUIDMetadataResult ->
                val data = result.data
                if (data != null) {
                    createUserFromMetadata(this, data)
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
                createChannelFromMetadata(this, pnChannelMetadata)
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
                    createChannelFromMetadata(this, pnChannelMetadata)
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


    override fun forwardMessage(message: Message, channelId: String): PNFuture<Unit> {
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
        ).then { Unit }
            .catch { exception ->
                Result.failure(PubNubException(FAILED_TO_FORWARD_MESSAGE.message, exception))
            }
    }


    override fun <T : EventContent> emitEvent(channel: String, payload: T): PNFuture<PNPublishResult> {
        return if (payload.method == EmitEventMethod.SIGNAL) {
            signal(channelId = channel, message = payload)
        } else {
            publish(channelId = channel, message = payload)
        }
    }

    override fun createDirectConversation(
        invitedUser: User,
        channelId: String?,
        channelData: Any?,
        membershipData: Any?
    ): PNFuture<CreateDirectConversationResult> {
        TODO("Not implemented yet")
//        val user = this.user ?: error("Chat user is not set. Set them by calling setChatUser on the Chat instance.")
//        val sortedUsers = listOf(invitedUser.id, user.id).sorted()
//        val finalChannelId = channelId ?: "direct${cyrb53a("${sortedUsers[0]}&${sortedUsers[1]}")}"
//
//
//        return getChannel(finalChannelId).thenAsync { channel -> // big fat TODO
//            if (channel == null) {
//                createChannel(finalChannelId, type = ChannelType.DIRECT).then {
//                    CreateDirectConversationResult()
//                }
//            } else {
//                CreateDirectConversationResult().asFuture()
//            }
//        }
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

    override fun publish(
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?,
    ): PNFuture<PNPublishResult> {
        return pubNub.publish(channelId, PNDataEncoder.encode(message)!!, meta, shouldStore, usePost, replicate, ttl)
    }

    override fun signal(
        channelId: String,
        message: EventContent,
    ): PNFuture<PNPublishResult> {
        return pubNub.signal(channelId, PNDataEncoder.encode(message)!!)
    }

    private fun isValidId(id: String): Boolean {
        return id.isNotEmpty()
    }

    private fun getChannelData(id: String): PNFuture<Channel> {
        return pubNub.getChannelMetadata(channel = id, includeCustom = false)
            .then { pnChannelMetadataResult: PNChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                    createChannelFromMetadata(this, pnChannelMetadata)
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
            custom = updatedUser.custom,
            includeCustom = false,
            type = updatedUser.type,
            status = updatedUser.status,
        ).then { pnUUIDMetadataResult ->
            pnUUIDMetadataResult.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                createUserFromMetadata(this, pnUUIDMetadata)
            } ?: error("PNUUIDMetadata is null.")
        }
    }


    private fun performUserDelete(user: User): PNFuture<User> = pubNub.removeUUIDMetadata(uuid = user.id).then { user }

    private fun createUserFromMetadata(chat: ChatImpl, pnUUIDMetadata: PNUUIDMetadata): User {
        return User(
            chat = chat,
            id = pnUUIDMetadata.id,
            name = pnUUIDMetadata.name,
            externalId = pnUUIDMetadata.externalId,
            profileUrl = pnUUIDMetadata.profileUrl,
            email = pnUUIDMetadata.email,
            custom = pnUUIDMetadata.custom?.let { createCustomObject(it) },
            status = pnUUIDMetadata.status,
            type = pnUUIDMetadata.type,
            updated = pnUUIDMetadata.updated,
        )
    }

    private fun createChannelFromMetadata(chat: ChatImpl, pnChannelMetadata: PNChannelMetadata): Channel {
        return Channel(
            chat = chat,
            id = pnChannelMetadata.id,
            name = pnChannelMetadata.name,
            custom = pnChannelMetadata.custom?.let { createCustomObject(it) },
            description = pnChannelMetadata.description,
            updated = pnChannelMetadata.updated,
            status = pnChannelMetadata.status,
            type = pnChannelMetadata.type?.let { ChannelType.valueOf(it.uppercase()) }
        )
    }

    private fun performSoftChannelDelete(channel: Channel): PNFuture<Channel> {
        val updatedChannel = channel.copy(status = DELETED)
        return pubNub.setChannelMetadata(
            channel = channel.id,
            name = updatedChannel.name,
            description = updatedChannel.description,
            custom = updatedChannel.custom,
            includeCustom = false,
            type = updatedChannel.type.toString().lowercase(),
            status = updatedChannel.status
        ).then { pnChannelMetadataResult ->
            pnChannelMetadataResult.data?.let { pnChannelMetadata: PNChannelMetadata ->
                createChannelFromMetadata(this, pnChannelMetadata)
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
                createChannelFromMetadata(this, pnChannelMetadata)
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
                createUserFromMetadata(this, pnUUIDMetadata)
            } ?: error("No data available to create User")
        }.catch { exception ->
            Result.failure(PubNubException(FAILED_TO_CREATE_UPDATE_USER_DATA.message, exception))
        }
    }
}
