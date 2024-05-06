package com.pubnub.kmp.models.consumers.objects.channel

actual data class PNChannelMetadataResult(
    actual val status: Int,
    actual val data: PNChannelMetadata?,
)

actual data class PNChannelMetadata(
    actual val id: String,
    actual val name: String?,
    actual val description: String?,
    actual val custom: Any?,
    actual val updated: String?,
    actual val eTag: String?,
    actual val type: String?,
    actual val status: String?,
)
