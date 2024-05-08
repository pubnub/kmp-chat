@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName


class ChannelType {
    var aaa = 0
}

@JsExport
@JsName("ChatConfig")
class ChatConfig(val pubnubConfig: PNConfiguration) {
    var uuid: String = ""
    var saveDebugLog: Boolean = false
    var typingTimeout: Int = 0
    var rateLimitPerChannel: Any = mutableMapOf<ChannelType, Int>()
}

private const val DELETED = "Deleted"

class ChatImpl(private val config: ChatConfig) : Chat {
    private val pubNub = PubNub(config.pubnubConfig)

    override fun createUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: Map<String, Any?>?,
        status: String?,
        type: String?,
        callback: (Result<User>) -> Unit,
    ) {
        pubNub.setUserMetadata(id, name, externalId, profileUrl, email, custom, includeCustom = true)
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
        custom: Map<String, Any?>?,
        status: String?,
        type: String?,
        updated: String?, //todo do we need this?
        callback: (Result<User>) -> Unit
    ) {
        validateId(id, callback)
        getUserData(id) { result ->
            result.fold(
                onSuccess = { user ->
                    pubNub.setUserMetadata(
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

    override fun deleteUser(id: String, softDelete: Boolean, callback: (Result<User>) -> Unit) {
        validateId(id, callback)
        getUserData(id) { result: Result<User> ->
            result.fold(
                onSuccess = { user ->
                    if (softDelete) {
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

    private fun validateId(id: String, callback: (Result<User>) -> Unit) {
        if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException("Id is required")))
        }
    }

    private fun getUserData(id: String, callback: (Result<User>) -> Unit) {
        pubNub.getUserMetadata(uuid = id, includeCustom = false).async { result ->
            result.fold(
                onSuccess = { pnUUIDMetadataResult ->
                    pnUUIDMetadataResult.data?.let { pnUUIDMetadata ->
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
        pubNub.setUserMetadata(
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
        pubNub.removeUserMetadata(uuid = user.id)
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
            custom = pnUUIDMetadata.custom as Map<String, Any?>?,
            status = pnUUIDMetadata.status,
            type = pnUUIDMetadata.type,
            updated = pnUUIDMetadata.updated,
        )
    }

//    fun updateUser(
//        id: String,
//        user: User,
//        callback: (Result<User>) -> Unit,
//    ): User {
//       pubNub.
//    }
}

//
//class User
