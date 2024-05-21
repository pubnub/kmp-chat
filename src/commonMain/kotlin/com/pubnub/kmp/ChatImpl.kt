package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.callbacks.fold
import com.pubnub.api.v2.callbacks.map


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
private const val CHANNEL_ID_IS_REQUIRED = "Channel ID is required"

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
                callback(result.map { it: PNUUIDMetadataResult ->
                    it.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                        createUserFromMetadata(this, pnUUIDMetadata)
                    } ?: run {
                        throw IllegalStateException("No data available to create User")
                    }
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
        if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(ID_IS_REQUIRED)))
            return
        }
        getUserData(id) { result ->
            result.fold(
                onSuccess = { _ ->
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
                        result.fold(
                            onSuccess = { pnUUIDMetadataResult ->
                                pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
                                    val updatedUser = createUserFromMetadata(this, pnUUIDMetadata)
                                    callback(Result.success(updatedUser))
                                }
                            },
                            onFailure = { error ->
                                callback(Result.failure(Exception("Failed to update user metadata: ${error.message}")))
                            }
                        )
                    }
                },
                onFailure = { error ->
                    callback(Result.failure(Exception(error)))
                }
            )
        }
    }

    override fun deleteUser(id: String, soft: Boolean, callback: (Result<User>) -> Unit) {
        if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(ID_IS_REQUIRED)))
            return
        }
        getUserData(id) { result: Result<User> ->
            result.fold(
                onSuccess = { user ->
                    if (soft) {
                        performSoftDelete(user, callback)
                    } else {
                        performHardDelete(user, callback)
                    }
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    override fun wherePresent(userId: String, callback: (Result<List<String>>) -> Unit) {
        if (userId.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(ID_IS_REQUIRED)))
            return
        }

        pubNub.whereNow(uuid = userId).async { result: Result<PNWhereNowResult> ->
            result.fold(
                onSuccess = { pnWhereNowResult ->
                    callback(Result.success(pnWhereNowResult.channels))
                },
                onFailure = { error ->
                    callback(Result.failure(Exception("Failed to retrieve wherePresent data: ${error.message}")))
                }
            )
        }
    }

    override fun isPresent(userId: String, channel: String, callback: (Result<Boolean>) -> Unit) {
        if (userId.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(ID_IS_REQUIRED)))
            return
        }
        if (channel.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(CHANNEL_ID_IS_REQUIRED)))
            return
        }

        pubNub.whereNow(uuid = userId).async { result: Result<PNWhereNowResult> ->
            result.fold(
                onSuccess = { pnWhereNowResult ->
                    callback(Result.success(pnWhereNowResult.channels.contains(channel)))
                },
                onFailure = { error ->
                    callback(Result.failure(Exception("Failed to retrieve isPresent data: ${error.message}")))
                }
            )
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
        if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(CHANNEL_ID_IS_REQUIRED)))
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
            result.fold(
                onSuccess = { pnChannelMetadataResult: PNChannelMetadataResult ->
                    pnChannelMetadataResult.data?.let { pnChannelMetadata ->
                        val updatedChannel: Channel = createChannelFromMetadata(this, pnChannelMetadata)
                        callback(Result.success(updatedChannel))
                    }
                },
                onFailure = { error ->
                    callback(Result.failure(Exception("Failed to update channel metadata: ${error.message}")))
                }
            )
        }
    }

    private fun getUserData(id: String, callback: (Result<User>) -> Unit) {
        pubNub.getUUIDMetadata(uuid = id, includeCustom = false).async { result: Result<PNUUIDMetadataResult> ->
            result.fold(
                onSuccess = { pnUUIDMetadataResult: PNUUIDMetadataResult ->
                    pnUUIDMetadataResult.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                        callback(Result.success(createUserFromMetadata(this, pnUUIDMetadata)))
                    } ?: callback(Result.failure(Exception("User metadata is empty")))
                },
                onFailure = { error ->
                    callback(Result.failure(Exception("Failed to retrieve user data: ${error.message}")))
                }
            )
        }
    }

    private fun performSoftDelete(user: User, callback: (Result<User>) -> Unit) {
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
            resultOfUpdate.fold(
                onSuccess = { pnUUIDMetadataResult ->
                    pnUUIDMetadataResult.data?.let { pnUUIDMetadata: PNUUIDMetadata ->
                        val updatedUserFromResponse = createUserFromMetadata(this, pnUUIDMetadata)
                        callback(Result.success(updatedUserFromResponse))
                    }
                },
                onFailure = { it: Throwable ->
                    callback(Result.failure(Exception("Failed to update user metadata: ${it.message}")))
                }
            )
        }
    }

    private fun performHardDelete(user: User, callback: (Result<User>) -> Unit) {
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
            type = pnChannelMetadata.type?.let { ChannelType.valueOf(pnChannelMetadata.type!!) }
        )
    }
}
