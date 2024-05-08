@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import ObjectsResponse
import Partial
import com.pubnub.kmp.models.consumers.objects.PNKey
import com.pubnub.kmp.models.consumers.objects.PNPage
import com.pubnub.kmp.models.consumers.objects.PNSortKey
import com.pubnub.kmp.models.consumers.objects.channel.PNChannelMetadataResult
import toOptional
import kotlin.js.Promise
import PubNub as PubNubJs

actual class PubNub actual constructor(configuration: PNConfiguration) {

    private val jsPubNub: PubNubJs = PubNubJs(configuration.toJs())

    actual fun publish(
        channel: String,
        message: Any,
        meta: Any?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?
    ): Endpoint<PNPublishResult> {
        return object : Endpoint<PNPublishResult> {
            override fun async(callback: (Result<PNPublishResult>) -> Unit) {
                val params = object : PubNubJs.PublishParameters {
                    override var message: Any = "myMessage"
                    override var channel: String = "myChannel"
                }
                jsPubNub.publish(params).then(onFulfilled = { it: PubNubJs.PublishResponse ->
                    callback(Result.success(PNPublishResult(it.timetoken.toString().toLong())))
                }, onRejected = { it: Throwable ->
                    callback(Result.failure(it))
                })
            }
        }
    }

    actual fun setUserMetadata(
        uuid: String?,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: Map<String, Any?>?,
        includeCustom: Boolean,
        type: String?,
        status: String?
    ): Endpoint<PNUserMetadataResult> {
        return Endpoint({
            val params = object : PubNubJs.SetUUIDMetadataParameters {
                override var data: PubNubJs.UUIDMetadata = UUIDMetadata(
                    name.toOptional(),
                    externalId.toOptional(),
                    profileUrl.toOptional(),
                    email.toOptional(),
                    status.toOptional(),
                    type.toOptional(),
                    custom.toOptional()
                )

                override var uuid: String? = uuid

                override var include: PubNubJs.`T$30`? = object : PubNubJs.`T$30` {
                    override var customFields: Boolean? = includeCustom
                }
            }

            jsPubNub.objects.setUUIDMetadata(params)
        }) { it: ObjectsResponse<PubNubJs.UUIDMetadataObject> ->
            PNUserMetadataResult(
                it.status.toInt(),
                with(it.data) {
                    PNUUIDMetadata(
                        id,
                        name,
                        externalId,
                        profileUrl,
                        email,
                        custom,
                        updated,
                        eTag,
                        type,
                        status
                    )
                }
            )
        }
    }

    actual fun removeUserMetadata(uuid: String?): Endpoint<PNRemoveMetadataResult> {
        return Endpoint({
            jsPubNub.objects.removeUUIDMetadata(object : PubNubJs.RemoveUUIDMetadataParameters {
                override var uuid: String? = uuid
            })
        }) { response ->
            PNRemoveMetadataResult(response.status.toInt())
        }
    }

    actual fun getUserMetadata(
        uuid: String?,
        includeCustom: Boolean
    ): Endpoint<PNUserMetadataResult> {
        TODO("Not yet implemented")
    }

    actual fun getAllUserMetadata(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        includeCount: Boolean,
        includeCustom: Boolean
    ): Endpoint<PNUserMetadataArrayResult> {
        TODO("Not yet implemented")
    }

    actual fun getAllChannelMetadata(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        includeCount: Boolean,
        includeCustom: Boolean
    ): Endpoint<Unit> {
        TODO("Not yet implemented")
    }

    /**
     * Returns metadata for the specified Channel, optionally including the custom data object for each.
     *
     * @param channel Channel name.
     * @param includeCustom Include respective additional fields in the response.
     */
    actual fun getChannelMetadata(
        channel: String,
        includeCustom: Boolean
    ): Endpoint<PNChannelMetadataResult> {
        TODO("Not yet implemented")
    }

    /**
     * Set metadata for a Channel in the database, optionally including the custom data object for each.
     *
     * @param channel Channel name.
     * @param name Name of a channel.
     * @param description Description of a channel.
     * @param custom Object with supported data types.
     * @param includeCustom Include respective additional fields in the response.
     */
    actual fun setChannelMetadata(
        channel: String,
        name: String?,
        description: String?,
        custom: Any?,
        includeCustom: Boolean,
        type: String?,
        status: String?
    ): Endpoint<PNChannelMetadataResult> {
        TODO("Not yet implemented")
    }

    /**
     * Removes the metadata from a specified channel.
     *
     * @param channel Channel name.
     */
    actual fun removeChannelMetadata(channel: String): Endpoint<PNRemoveMetadataResult> {
        TODO("Not yet implemented")
    }
}

actual class PNPublishResult(
    actual val timetoken: Long
)

private fun <T, U> Endpoint(promiseFactory: () -> Promise<T>, responseMapping: (T) -> U): Endpoint<U> =
    object : Endpoint<U> {
        override fun async(callback: (Result<U>) -> Unit) {
            promiseFactory().then(
                onFulfilled = { response: T ->
                    callback(Result.success(responseMapping(response)))
                },
                onRejected = { throwable ->
                    callback(Result.failure(throwable))
                }
            )
        }
    }

fun UUIDMetadata(
    name: Optional<String?>,
    externalId: Optional<String?>,
    profileUrl: Optional<String?>,
    email: Optional<String?>,
    status: Optional<String?>,
    type: Optional<String?>,
    custom: Optional<Map<String, Any?>?>
): PubNubJs.UUIDMetadata {
    val result: PubNubJs.UUIDMetadata = createJsObject()
    name.onValue { result.name = it }
    externalId.onValue { result.externalId = it }
    profileUrl.onValue { result.profileUrl = it }
    email.onValue { result.email = it }
    status.onValue { result.status = it }
    type.onValue { result.type = it }
    custom.onValue { result.custom = it?.toCustomObject() }
    return result
}

fun Map<String, Any?>.toCustomObject(): PubNubJs.CustomObject {
    val custom = Any().asDynamic()
    entries.forEach {
        custom[it.key] = it.value
    }
    return custom
}

fun <T : Partial> createJsObject(): T = Any().asDynamic() as T

fun PNConfiguration.toJs(): PubNubJs.PNConfiguration {
    val config: PubNubJs.PNConfiguration = createJsObject()
    config.userId = userId.value
    config.subscribeKey = subscribeKey
    config.publishKey = publishKey
//    config.cipherKey
//    config.authKey: String?
//    config.logVerbosity: Boolean?
//    config.ssl: Boolean?
//    config.origin: dynamic /* String? | Array<String>? */
//    config.presenceTimeout: Number?
//    config.heartbeatInterval: Number?
//    config.restore: Boolean?
//    config.keepAlive: Boolean?
//    config.keepAliveSettings: KeepAliveSettings?
//    config.subscribeRequestTimeout: Number?
//    config.suppressLeaveEvents: Boolean?
//    config.secretKey: String?
//    config.requestMessageCountThreshold: Number?
//    config.autoNetworkDetection: Boolean?
//    config.listenToBrowserNetworkEvents: Boolean?
//    config.useRandomIVs: Boolean?
//    config.dedupeOnSubscribe: Boolean?
//    config.cryptoModule: CryptoModule?
//    config.retryConfiguration: dynamic /* LinearRetryPolicyConfiguration? | ExponentialRetryPolicyConfiguration? */
//    config.enableEventEngine: Boolean?
//    config.maintainPresenceState: Boolean?
    return config
}