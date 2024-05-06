@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.pubnub.kmp

import com.pubnub.kmp.models.consumers.objects.PNPage
import kotlin.js.JsExport

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

actual class PNUserMetadataResult(
    actual val status: Int,
    actual val data: PNUUIDMetadata?,
)

actual class PNRemoveMetadataResult(
    actual val status: Int
)

actual data class PNUserMetadataArrayResult(
    actual val status: Int,
    actual val data: Collection<PNUUIDMetadata>,
    actual val totalCount: Int?,
    actual val next: PNPage.PNNext?,
    actual val prev: PNPage.PNPrev?,
)