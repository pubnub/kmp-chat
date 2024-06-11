package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataArrayResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.callbacks.mapCatching
import com.pubnub.api.v2.callbacks.wrapException
import com.pubnub.kmp.channel.GetChannelsResponse
import com.pubnub.kmp.error.PubNubErrorMessage.CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL
import com.pubnub.kmp.error.PubNubErrorMessage.CHANNEL_ID_ALREADY_EXIST
import com.pubnub.kmp.error.PubNubErrorMessage.CHANNEL_META_DATA_IS_EMPTY
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_CHANNEL_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_CREATE_UPDATE_USER_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_DELETE_USER
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_FORWARD_MESSAGE
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_GET_CHANNELS
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_GET_USERS
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_CHANNEL_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_IS_PRESENT_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_USER_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_SOFT_DELETE_CHANNEL
import com.pubnub.kmp.error.PubNubErrorMessage.FAILED_TO_UPDATE_USER_METADATA
import com.pubnub.kmp.error.PubNubErrorMessage.FOR_PUBLISH_PAYLOAD_SHOULD_BE_OF_TYPE_TEXT_MESSAGE_CONTENT
import com.pubnub.kmp.error.PubNubErrorMessage.USER_ID_ALREADY_EXIST
import com.pubnub.kmp.error.PubNubErrorMessage.USER_NOT_EXIST
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.user.GetUsersResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


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
    override fun createUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?,
        callback: (Result<User>) -> Unit,
    ) {
        if (!isValidId(id, ID_IS_REQUIRED, callback)) {
            return
        }
        getUser(id) { result: Result<User?> ->
            result.onSuccess { user: User? ->
                user?.let {
                    callback(Result.failure(Exception(USER_ID_ALREADY_EXIST.message)))
                } ?: setUserMetadata(id, name, externalId, profileUrl, email, custom, type, status, callback)
            }.onFailure { pnException ->
                callback(Result.failure(pnException))
            }
        }
    }

    override fun getUser(userId: String, callback: (Result<User?>) -> Unit) {
        if (!isValidId(userId, ID_IS_REQUIRED, callback)) {
            return
        }
        pubNub.getUUIDMetadata(uuid = userId).async { result: Result<PNUUIDMetadataResult> ->
            result.onSuccess { pnUUIDMetadataResult: PNUUIDMetadataResult ->
                pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                    try {
                        val retrievedUser = createUserFromMetadata(this, pnUUIDMetadata)
                        callback(Result.success(retrievedUser))
                    } catch (exception: Exception) {
                        callback(Result.failure(exception))
                    }
                }
                    ?: callback(Result.failure(Exception(FAILED_TO_RETRIEVE_USER_DATA.message.plus("PNUUIDMetadataResult is null"))))
            }.onFailure { pnException ->
                if (pnException.statusCode == HTTP_ERROR_404) {
                    callback(Result.success(null))
                } else {
                    callback(Result.failure(pnException))
                }
            }
        }
    }

    override fun getUsers(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
        callback: (Result<GetUsersResponse>) -> Unit
    ) {
        pubNub.getAllUUIDMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).async { result: Result<PNUUIDMetadataArrayResult> ->
            val res: Result<GetUsersResponse> = result.mapCatching { pnUUIDMetadataArrayResult ->
                val users: MutableSet<User> = pnUUIDMetadataArrayResult.data.map { pnUUIDMetadata ->
                    createUserFromMetadata(this, pnUUIDMetadata)
                }.toMutableSet()
                GetUsersResponse(
                    users = users,
                    next = pnUUIDMetadataArrayResult.next,
                    prev = pnUUIDMetadataArrayResult.prev,
                    total = pnUUIDMetadataArrayResult.totalCount ?: 0
                )
            }.wrapException { pnException ->
                PubNubException(FAILED_TO_GET_USERS.message, pnException)
            }
            callback(res)
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
        type: String?,
        callback: (Result<User>) -> Unit
    ) {
        if (!isValidId(id, ID_IS_REQUIRED, callback)) {
            return
        }
        getUser(id) { result ->
            result.onSuccess { _ ->
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
                ).async { result: Result<PNUUIDMetadataResult> ->
                    val res: Result<User> = result.mapCatching { pnUUIDMetadataResult ->
                        pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                            createUserFromMetadata(this, pnUUIDMetadata)
                        } ?: error("Failed to update user metadata. PNUUIDMetadata is null.")
                    }.wrapException { pnException ->
                        PubNubException(FAILED_TO_UPDATE_USER_METADATA.message, pnException)
                    }
                    callback(res)
                }
            }.onFailure { pnException ->
                callback(Result.failure(pnException))
            }
        }
    }

    override fun deleteUser(id: String, soft: Boolean, callback: (Result<User>) -> Unit) {
        if (!isValidId(id, ID_IS_REQUIRED, callback)) {
            return
        }
        getUser(id) { result: Result<User?> ->
            result.onSuccess { user: User? ->
                user?.let { notNullUser ->
                    if (soft) {
                        performSoftUserDelete(notNullUser, callback)
                    } else {
                        performUserDelete(notNullUser, callback)
                    }
                } ?: callback(Result.failure(Exception(USER_NOT_EXIST.message)))
            }.onFailure { error ->
                callback(Result.failure(error))
            }
        }
    }

    override fun wherePresent(userId: String, callback: (Result<List<String>>) -> Unit) {
        if (!isValidId(userId, ID_IS_REQUIRED, callback)) {
            return
        }

        pubNub.whereNow(uuid = userId).async { result: Result<PNWhereNowResult> ->
            result.onSuccess { pnWhereNowResult ->
                callback(Result.success(pnWhereNowResult.channels))
            }.onFailure { pnException ->
                callback(Result.failure(Exception(FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA.message, pnException)))
            }
        }
    }

    override fun isPresent(userId: String, channel: String, callback: (Result<Boolean>) -> Unit) {
        if (!isValidId(userId, ID_IS_REQUIRED, callback)) {
            return
        }
        if (!isValidId(channel, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }

        pubNub.whereNow(uuid = userId).async { result: Result<PNWhereNowResult> ->
            result.onSuccess { pnWhereNowResult ->
                callback(Result.success(pnWhereNowResult.channels.contains(channel)))
            }.onFailure { pnException ->
                callback(Result.failure(Exception(FAILED_TO_RETRIEVE_IS_PRESENT_DATA.message, pnException)))
            }
        }
    }

    override fun createChannel(
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?,
        callback: (Result<Channel>) -> Unit
    ) {
        if (!isValidId(id, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }
        getChannel(id) { result: Result<Channel?> ->
            result.onSuccess { channel: Channel? ->
                channel?.let {
                    callback(Result.failure(Exception(CHANNEL_ID_ALREADY_EXIST.message)))
                } ?: setChannelMetadata(id, name, description, custom, type, status, callback)
            }.onFailure { pnException ->
                callback(Result.failure(pnException))
            }
        }
    }

    override fun getChannel(channelId: String, callback: (Result<Channel?>) -> Unit) {
        if (!isValidId(channelId, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }
        pubNub.getChannelMetadata(channel = channelId).async { result: Result<PNChannelMetadataResult> ->
            result.onSuccess { pnChannelMetadataResult: PNChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata: PNChannelMetadata ->
                    try {
                        val retrievedChannel: Channel = createChannelFromMetadata(this, pnChannelMetadata)
                        callback(Result.success(retrievedChannel))
                    } catch (exception: Exception) {
                        callback(Result.failure(exception))
                    }
                }
                    ?: callback(Result.failure(Exception(FAILED_TO_CREATE_UPDATE_CHANNEL_DATA.message.plus("PNChannelMetadata is null"))))
            }.onFailure { pnException ->
                if (pnException.statusCode == HTTP_ERROR_404) {
                    callback(Result.success(null))
                } else {
                    callback(Result.failure(pnException))
                }
            }
        }
    }

    override fun getChannels(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?,
        callback: (Result<GetChannelsResponse>) -> Unit
    ) {
        pubNub.getAllChannelMetadata(
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = true,
            includeCustom = true
        ).async { result: Result<PNChannelMetadataArrayResult> ->
            result.onSuccess { pnChannelMetadataArrayResult ->
                val channels: MutableSet<Channel> = pnChannelMetadataArrayResult.data.map { pnChannelMetadata ->
                    createChannelFromMetadata(this, pnChannelMetadata)
                }.toMutableSet()
                val response = GetChannelsResponse(
                    channels = channels,
                    next = pnChannelMetadataArrayResult.next,
                    prev = pnChannelMetadataArrayResult.prev,
                    total = pnChannelMetadataArrayResult.totalCount ?: 0
                )
                callback(Result.success(response))
            }.onFailure { pnException ->
                callback(Result.failure(Exception(FAILED_TO_GET_CHANNELS.message, pnException)))
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
        type: ChannelType?,
        callback: (Result<Channel>) -> Unit
    ) {
        if (!isValidId(id, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }
        getChannel(id) { result: Result<Channel?> ->
            result.onSuccess { channel: Channel? ->
                channel?.let {
                    setChannelMetadata(id, name, description, custom, type, status, callback)
                } ?: callback(Result.failure(Exception("Channel not found")))
            }.onFailure { pnException ->
                callback(Result.failure(pnException))
            }
        }
    }

    override fun deleteChannel(id: String, soft: Boolean, callback: (Result<Channel>) -> Unit) {
        if (!isValidId(id, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }
        getChannelData(id) { result: Result<Channel> ->
            result.onSuccess { channel: Channel ->
                if (soft) {
                    performSoftChannelDelete(channel, callback)
                } else {
                    performChannelDelete(channel, callback)
                }
            }.onFailure { pnException ->
                callback(Result.failure(pnException))
            }
        }
    }

    override fun forwardMessage(message: Message, channelId: String, callback: (Result<Unit>) -> Unit) {
        if (!isValidId(channelId, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }
        if (message.channelId == channelId) {
            callback(Result.failure(IllegalArgumentException(CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL.message)))
            return
        }

        val meta = message.meta?.toMutableMap() ?: mutableMapOf()
        meta[ORIGINAL_PUBLISHER] = message.userId

        pubNub.publish(
            message = message.content,
            channel = channelId,
            meta = meta,
            ttl = message.timetoken.toInt()
        ).async { result: Result<PNPublishResult> ->
            result.onSuccess {
                callback(Result.success(Unit))
            }.onFailure { pnException ->
                callback(Result.failure(Exception(FAILED_TO_FORWARD_MESSAGE.message, pnException)))
            }
        }
    }

    override fun <T : EventContent> emitEvent(
        channel: String,
        payload: T,
        callback: (Result<PNPublishResult>) -> Unit
    ) {
        if (payload.method == EmitEventMethod.SIGNAL) {
            pubNub.signal(channel = channel, message = payload).async(callback)
        } else {
            val message: EventContent.TextMessageContent
            try {
                message = payload as EventContent.TextMessageContent
            } catch (exception: ClassCastException) {
                callback(
                    Result.failure(
                        Exception(
                            FOR_PUBLISH_PAYLOAD_SHOULD_BE_OF_TYPE_TEXT_MESSAGE_CONTENT.message,
                            exception
                        )
                    )
                )
                return
            }
            pubNub.publish(channel = channel, message = message).async(callback)
        }
    }

    override fun whoIsPresent(channelId: String, callback: (Result<Collection<String>>) -> Unit) {
        if (!isValidId(channelId, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }
        pubNub.hereNow(listOf(channelId)).async { result ->
            result.onSuccess {
                val occupants =
                    it.channels[channelId]?.occupants?.map(PNHereNowOccupantData::uuid) ?: emptyList()
                callback(Result.success(occupants))
            }.onFailure { pnException ->
                callback(
                    Result.failure(
                        Exception(
                            FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA.message,
                            pnException
                        )
                    )
                )
            }
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
        callback: (Result<PNPublishResult>) -> Unit
    ) {
        pubNub.publish(channelId, message, meta, shouldStore, usePost, replicate, ttl).async(callback)
    }

    private fun <T> isValidId(id: String, errorMessage: String, callback: (Result<T>) -> Unit): Boolean {
        return if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(errorMessage)))
            false
        } else {
            true
        }
    }

    private fun getChannelData(id: String, callback: (Result<Channel>) -> Unit) {
        pubNub.getChannelMetadata(channel = id, includeCustom = false)
            .async { result: Result<PNChannelMetadataResult> ->
                result.onSuccess { pnChannelMetadataResult: PNChannelMetadataResult ->
                    pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                        callback(Result.success(createChannelFromMetadata(this, pnChannelMetadata)))
                    } ?: callback(Result.failure(Exception(CHANNEL_META_DATA_IS_EMPTY.message)))
                }.onFailure { pnException ->
                    callback(Result.failure(Exception(FAILED_TO_RETRIEVE_CHANNEL_DATA.message, pnException)))
                }
            }
    }

    private fun performSoftUserDelete(user: User, callback: (Result<User>) -> Unit) {
        val updatedUser = user.copy(status = DELETED)
        pubNub.setUUIDMetadata(
            uuid = user.id,
            name = updatedUser.name,
            externalId = updatedUser.externalId,
            profileUrl = updatedUser.profileUrl,
            email = updatedUser.email,
            custom = updatedUser.custom,
            includeCustom = false,
            type = updatedUser.type,
            status = updatedUser.status,
        ).async { resultOfUpdate: Result<PNUUIDMetadataResult> ->
            val res: Result<User> = resultOfUpdate.mapCatching { pnUUIDMetadataResult ->
                pnUUIDMetadataResult.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                    createUserFromMetadata(this, pnUUIDMetadata)
                } ?: error("Failed to update user metadata. PNUUIDMetadata is null.")
            }.wrapException { pnException ->
                PubNubException(FAILED_TO_UPDATE_USER_METADATA.message, pnException)
            }
            callback(res)
        }
    }

    private fun performUserDelete(user: User, callback: (Result<User>) -> Unit) {
        pubNub.removeUUIDMetadata(uuid = user.id)
            .async { removeResult: Result<PNRemoveMetadataResult> ->
                if (removeResult.isSuccess) {
                    callback(Result.success(user))
                } else {
                    callback(
                        Result.failure(
                            PubNubException(
                                FAILED_TO_DELETE_USER.message,
                                removeResult.exceptionOrNull()
                            )
                        )
                    )
                }
            }
    }

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

    private fun performSoftChannelDelete(channel: Channel, callback: (Result<Channel>) -> Unit) {
        val updatedChannel = channel.copy(status = DELETED)
        pubNub.setChannelMetadata(
            channel = channel.id,
            name = updatedChannel.name,
            description = updatedChannel.description,
            custom = updatedChannel.custom,
            includeCustom = false,
            type = updatedChannel.type.toString().lowercase(),
            status = updatedChannel.status
        ).async { result: Result<PNChannelMetadataResult> ->
            val res: Result<Channel> = result.mapCatching { pnChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata: PNChannelMetadata ->
                    createChannelFromMetadata(this, pnChannelMetadata)
                } ?: error("Failed to update channel metadata. PNChannelMetadata is null.")
            }.wrapException { pnException ->
                PubNubException(FAILED_TO_SOFT_DELETE_CHANNEL.message, pnException)
            }
            callback(res)
        }
    }

    private fun performChannelDelete(channel: Channel, callback: (Result<Channel>) -> Unit) {
        pubNub.removeChannelMetadata(channel = channel.id).async { result: Result<PNRemoveMetadataResult> ->
            result.onSuccess {
                callback(Result.success(channel))
            }.onFailure { exception ->
                callback(Result.failure(Exception("Failed to delete channel: ${exception.message}")))
            }
        }
    }

    private fun setChannelMetadata(
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?,
        callback: (Result<Channel>) -> Unit
    ) {
        pubNub.setChannelMetadata(
            channel = id,
            name = name,
            description = description,
            custom = custom,
            includeCustom = true,
            type = type.toString().lowercase(),
            status = status
        ).async { result: Result<PNChannelMetadataResult> ->
            val res: Result<Channel> = result.mapCatching { pnChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                    createChannelFromMetadata(this, pnChannelMetadata)
                } ?: error("No data available to create Channel")
            }.wrapException { pnException ->
                PubNubException(FAILED_TO_CREATE_UPDATE_CHANNEL_DATA.message, pnException)
            }
            callback(res)
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
        callback: (Result<User>) -> Unit
    ) {
        pubNub.setUUIDMetadata(
            uuid = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            includeCustom = true,
            type = type,
            status = status
        )
            .async { result: Result<PNUUIDMetadataResult> ->
                val res: Result<User> = result.mapCatching { pnUUIDMetadataResult ->
                    pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                        createUserFromMetadata(this, pnUUIDMetadata)
                    } ?: error("No data available to create User")
                }.wrapException { pnException ->
                    PubNubException(FAILED_TO_CREATE_UPDATE_USER_DATA.message, pnException)
                }
                callback(res)
            }
    }
}
