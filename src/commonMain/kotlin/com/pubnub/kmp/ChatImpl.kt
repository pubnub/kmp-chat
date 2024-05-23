package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.callbacks.mapCatching
import com.pubnub.kmp.types.TextMessageContent


interface ChatConfig {
    val pubnubConfig: PNConfiguration
    var uuid: String
    var saveDebugLog: Boolean
    var typingTimeout: Int
    var rateLimitPerChannel: Any
}

class ChatConfigImpl(override val pubnubConfig: PNConfiguration) : ChatConfig {
    override var uuid: String = ""
    override var saveDebugLog: Boolean = false
    override var typingTimeout: Int = 0
    override var rateLimitPerChannel: Any = mutableMapOf<ChannelType, Int>()
}

private const val DELETED = "Deleted"

private const val ID_IS_REQUIRED = "Id is required"
private const val CHANNEL_ID_IS_REQUIRED = "Channel Id is required"
private const val ORIGINAL_PUBLISHER = "originalPublisher"

class ChatImpl(
    private val config: ChatConfig,
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
        pubNub.setUUIDMetadata(id, name, externalId, profileUrl, email, custom, includeCustom = true)
            .async { result: Result<PNUUIDMetadataResult> ->
                callback(result.mapCatching { it: PNUUIDMetadataResult ->
                    it.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                        createUserFromMetadata(this, pnUUIDMetadata)
                    } ?: throw IllegalStateException("No data available to create User")
                })
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
        getUserData(id) { result ->
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
                    result.onSuccess { pnUUIDMetadataResult ->
                        pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                            val updatedUser = createUserFromMetadata(this, pnUUIDMetadata)
                            callback(Result.success(updatedUser))
                        } ?: callback(Result.failure(Exception("Failed to update user metadata. PNUUIDMetadata is null.")))
                    }.onFailure { error ->
                        callback(Result.failure(Exception("Failed to update user metadata: ${error.message}")))
                    }
                }
            }.onFailure { error ->
                callback(Result.failure(Exception(error)))
            }
        }
    }

    override fun deleteUser(id: String, soft: Boolean, callback: (Result<User>) -> Unit) {
        if (!isValidId(id, ID_IS_REQUIRED, callback)) {
            return
        }
        getUserData(id) { result: Result<User> ->
            result.onSuccess { user ->
                if (soft) {
                    performSoftUserDelete(user, callback)
                } else {
                    performUserDelete(user, callback)
                }
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
            }.onFailure { error ->
                callback(Result.failure(Exception("Failed to retrieve wherePresent data: ${error.message}")))
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
            }.onFailure { error ->
                callback(Result.failure(Exception("Failed to retrieve isPresent data: ${error.message}")))
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
        pubNub.setChannelMetadata(
            channel = id,
            name = name,
            description = description,
            custom = custom,
            includeCustom = true,
            type = type.toString(),
            status = status
        ).async { result: Result<PNChannelMetadataResult> ->
            result.onSuccess { pnChannelMetadataResult: PNChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                    val updatedChannel: Channel = createChannelFromMetadata(this, pnChannelMetadata)
                    callback(Result.success(updatedChannel))
                } ?: callback(Result.failure(Exception("Failed to update channel metadata. PNChannelMetadata is null.")))
            }.onFailure { error ->
                callback(Result.failure(Exception("Failed to update channel metadata: ${error.message}")))
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
            }.onFailure { error ->
                callback(Result.failure(error))
            }
        }
    }

    override fun forwardMessage(message: Message, channelId: String, callback: (Result<Unit>) -> Unit) {
        if (!isValidId(channelId, CHANNEL_ID_IS_REQUIRED, callback)) {
            return
        }
        if (message.channelId == channelId) {
            callback(Result.failure(IllegalArgumentException("You cannot forward the message to the same channel")))
            return
        }

        val meta = message.meta?.toMutableMap() ?: mutableMapOf()
        meta[ORIGINAL_PUBLISHER] = message.userId

        publish(
            message = message.content,
            channel = channelId,
            storeInHistory = null,
            sendByPost = null,
            meta = meta,
            ttl = message.timetoken.toInt(),
            callback = { result: Result<PNPublishResult> ->
                result.onSuccess {
                    callback(Result.success(Unit))
                }.onFailure { exception: PubNubException ->
                    callback(Result.failure(exception))
                }
            }
        )
    }

    private fun publish(
        message: TextMessageContent,
        channel: String,
        storeInHistory: Boolean?,
        sendByPost: Boolean?,
        meta: Any?,
        ttl: Int?,
        callback: (Result<PNPublishResult>) -> Unit
    ) {
        pubNub.publish(
            channel = channel,
            message = message,
            meta = meta,
            shouldStore = storeInHistory,
            usePost = sendByPost?.let { it } ?: false,
            replicate = true,
            ttl = ttl
        ).async { result: Result<PNPublishResult> ->
            result.onSuccess {
                callback(result)
            }.onFailure { exception: PubNubException ->
                callback(Result.failure(exception))
            }
        }
    }

    private fun <T> isValidId(id: String, errorMessage: String, callback: (Result<T>) -> Unit): Boolean {
        return if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(errorMessage)))
            false
        } else {
            true
        }
    }

    private fun getUserData(id: String, callback: (Result<User>) -> Unit) {
        pubNub.getUUIDMetadata(uuid = id, includeCustom = false).async { result: Result<PNUUIDMetadataResult> ->
            result.onSuccess { pnUUIDMetadataResult: PNUUIDMetadataResult ->
                pnUUIDMetadataResult.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                    callback(Result.success(createUserFromMetadata(this, pnUUIDMetadata)))
                } ?: callback(Result.failure(Exception("User metadata is empty")))
            }.onFailure { error ->
                callback(Result.failure(Exception("Failed to retrieve user data: ${error.message}")))
            }
        }
    }

    private fun getChannelData(id: String, callback: (Result<Channel>) -> Unit) {
        pubNub.getChannelMetadata(channel = id, includeCustom = false)
            .async { result: Result<PNChannelMetadataResult> ->
                result.onSuccess { pnChannelMetadataResult: PNChannelMetadataResult ->
                    pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                        callback(Result.success(createChannelFromMetadata(this, pnChannelMetadata)))
                    } ?: callback(Result.failure(Exception("Channel metadata is empty")))
                }.onFailure { error ->
                    callback(Result.failure(Exception("Failed to retrieve channel data: ${error.message}")))
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
            resultOfUpdate.onSuccess { pnUUIDMetadataResult ->
                pnUUIDMetadataResult.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                    val updatedUserFromResponse = createUserFromMetadata(this, pnUUIDMetadata)
                    callback(Result.success(updatedUserFromResponse))
                } ?: callback(Result.failure(Exception("Failed to update user metadata. PNUUIDMetadata is null.")))
            }.onFailure { it: Throwable ->
                callback(Result.failure(Exception("Failed to update user metadata: ${it.message}")))
            }
        }
    }

    private fun performUserDelete(user: User, callback: (Result<User>) -> Unit) {
        pubNub.removeUUIDMetadata(uuid = user.id)
            .async { removeResult: Result<PNRemoveMetadataResult> ->
                if (removeResult.isSuccess) {
                    callback(Result.success(user))
                } else {
                    callback(Result.failure(Exception("Unable to delete user")))
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
            type = pnChannelMetadata.type?.let { ChannelType.valueOf(it) }
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
            type = updatedChannel.type.toString(),
            status = updatedChannel.status
        ).async { result: Result<PNChannelMetadataResult> ->
            result.onSuccess { pnChannelMetadataResult: PNChannelMetadataResult ->
                pnChannelMetadataResult.data?.let { pnChannelMetadata: PNChannelMetadata ->
                    val updatedChannelFromResponse = createChannelFromMetadata(this, pnChannelMetadata)
                    callback(Result.success(updatedChannelFromResponse))
                } ?: callback(Result.failure(Exception("Failed to update channel metadata. PNChannelMetadata is null.")))
            }.onFailure { exception: Throwable ->
                callback(Result.failure(Exception("Failed to soft delete channel: ${exception.message}")))
            }
        }
    }

    private fun performChannelDelete(channel: Channel, callback: (Result<Channel>) -> Unit) {
        pubNub.removeChannelMetadata(channel = channel.id).async { result: Result<PNRemoveMetadataResult> ->
            result.onSuccess { pnRemoveMetadataResult: PNRemoveMetadataResult ->
                callback(Result.success(channel))
            }.onFailure { exception ->
                callback(Result.failure(Exception("Failed to delete channel: ${exception.message}")))
            }
        }
    }

}
