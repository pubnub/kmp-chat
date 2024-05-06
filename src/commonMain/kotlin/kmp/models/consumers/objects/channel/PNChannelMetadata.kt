package com.pubnub.kmp.models.consumers.objects.channel

expect class PNChannelMetadata {
    val id: String
    val name: String?
    val description: String?
    val custom: Any?
    val updated: String?
    val eTag: String?
    val type: String?
    val status: String?
}

expect class PNChannelMetadataResult {
    val status: Int
    val data: PNChannelMetadata?
}