@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import kotlin.js.ExperimentalJsExport

interface Endpoint<T> {
    fun async(callback: (Result<T>)-> Unit)
}

expect class PubNub(configuration: PNConfiguration) {
    fun publish(channel: String,
                message: Any,
                meta: Any? = null,
                shouldStore: Boolean? = true,
                usePost: Boolean = false,
                replicate: Boolean = true,
                ttl: Int? = null,
    ): Endpoint<PNPublishResult>

    fun setUserMetadata(
        uuid: String? = null,
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: Any? = null,
        includeCustom: Boolean = false,
        type: String? = null,
        status: String? = null,
    ): Endpoint<PNUUIDMetadataResult>

    fun removeUserMetadata(uuid: String? = null): Endpoint<PNRemoveMetadataResult>
    fun getUserMetadata(uuid: String?, includeCustom: Boolean): Endpoint<PNUUIDMetadataResult>
}

expect class PNPublishResult {
    val timetoken: Long
}

expect class PNUUIDMetadata {
    val id: String
    val name: String?
    val externalId: String?
    val profileUrl: String?
    val email: String?
    val custom: Any?
    val updated: String?
    val eTag: String?
    val type: String?
    val status: String?
}

expect class PNUUIDMetadataResult {
    val status: Int
    val data: PNUUIDMetadata?
}

expect class PNRemoveMetadataResult {
    val status: Int
}