package com.pubnub.kmp

import com.pubnub.api.CustomObject
import com.pubnub.api.PubNub
import com.pubnub.api.createPubNub
import com.pubnub.api.createCustomObject
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNRemoveMetadataResult
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.callbacks.fold
import com.pubnub.api.v2.callbacks.map
import com.pubnub.kmp.membership.IncludeParameters
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.membership.MembershipsResponse


interface ChatConfig{
    val pubnubConfig: PNConfiguration
    var uuid: String
    var saveDebugLog: Boolean
    var typingTimeout: Int
    var rateLimitPerChannel: Any
}

class ChatConfigImpl(override val pubnubConfig: PNConfiguration): ChatConfig {
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
    private val pubnub: PubNub = createPubNub(config.pubnubConfig)
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
        pubnub.setUUIDMetadata(id, name, externalId, profileUrl, email, custom, includeCustom = true)
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
                onSuccess = { user ->
                    pubnub.setUUIDMetadata(
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

    override fun wherePresent(id: String, callback: (Result<List<String>>) -> Unit) {
        if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(ID_IS_REQUIRED)))
            return
        }

        pubnub.whereNow(uuid = id).async { result: Result<PNWhereNowResult> ->
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

    override fun isPresent(id: String, channel: String, callback: (Result<Boolean>) -> Unit) {
        if (id.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(ID_IS_REQUIRED)))
            return
        }
        if (channel.isEmpty()) {
            callback(Result.failure(IllegalArgumentException(CHANNEL_ID_IS_REQUIRED)))
            return
        }

        pubnub.whereNow(uuid = id).async { result: Result<PNWhereNowResult> ->
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

    override fun getMembership(
        user: User,
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMembershipKey>>,
        includeParameters: IncludeParameters,
        callback: (kotlin.Result<MembershipsResponse>) -> Unit
    ) {
        val id = user.id
        if (id.isEmpty()) {
            callback(kotlin.Result.failure(IllegalArgumentException(ID_IS_REQUIRED)))
            return
        }

        pubnub.getMemberships(
            uuid = id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCount = includeParameters.totalCount,
            includeCustom = includeParameters.customFields,
            includeChannelDetails = getChannelDetailsType(includeParameters.customChannelFields)
        ).async { result: Result<PNChannelMembershipArrayResult> ->
            result.fold(
                onSuccess = { pnChannelMembershipArrayResult ->
                    val membershipsResponse = MembershipsResponse(
                        next = pnChannelMembershipArrayResult.next,
                        prev = pnChannelMembershipArrayResult.prev,
                        total = pnChannelMembershipArrayResult.totalCount ?: 0,
                        status = pnChannelMembershipArrayResult.status.toString(),
                        memberships = getMemberships(pnChannelMembershipArrayResult, user)
                    )
                    callback(kotlin.Result.success(membershipsResponse))
                },
                onFailure = { error ->
                    callback(kotlin.Result.failure(Exception("Failed to retrieve getMembership data: ${error.message}")))
                }
            )
        }
    }

    private fun getMemberships(
        pnChannelMembershipArrayResult: PNChannelMembershipArrayResult,
        user: User
    ): List<Membership> {
        val memberships: List<Membership> =
            pnChannelMembershipArrayResult.data.map { pnChannelMembership: PNChannelMembership ->
                Membership(
                    channel = getChannel(pnChannelMembership),
                    user = user,
                    custom = pnChannelMembership.custom,
                )
            }
        return memberships
    }

    private fun getChannel(pnChannelMembership: PNChannelMembership): Channel {
        return Channel(
            chat = this,
            id = pnChannelMembership.channel?.id ?: "undefined", //todo not sure about this
            name = pnChannelMembership.channel?.name,
            custom = pnChannelMembership.custom,
            description = pnChannelMembership.channel?.description,
            updated = pnChannelMembership.channel?.updated,
            status = pnChannelMembership.channel?.status,
            type = ChannelType.DIRECT, //todo not sure about this
        )
    }

    private fun getChannelDetailsType(includeChannelWithCustom: Boolean): PNChannelDetailsLevel {
        return if (includeChannelWithCustom) {
            PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM
        } else {
            PNChannelDetailsLevel.CHANNEL
        }
    }


    private fun getUserData(id: String, callback: (Result<User>) -> Unit) {
        pubnub.getUUIDMetadata(uuid = id, includeCustom = false).async { result: Result<PNUUIDMetadataResult> ->
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
        pubnub.setUUIDMetadata(
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
        pubnub.removeUUIDMetadata(uuid = user.id)
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
}
