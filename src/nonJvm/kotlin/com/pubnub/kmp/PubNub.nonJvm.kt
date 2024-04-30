@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.pubnub.kmp

actual class PNUUIDMetadata(
    actual val id: String,
    actual val name: String?,
    actual val externalId: String?,
    actual val profileUrl: String?,
    actual val email: String?,
    actual val custom: Any?,
    actual val updated: String?,
    actual val eTag: String?,
    actual val type: String?,
    actual val status: String?,
)

actual class PNUUIDMetadataResult(
    actual val status: Int,
    actual val data: PNUUIDMetadata?,
)

actual class PNRemoveMetadataResult(
    actual val status: Int
)