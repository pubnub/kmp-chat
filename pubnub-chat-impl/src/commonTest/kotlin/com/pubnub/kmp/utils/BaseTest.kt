package com.pubnub.kmp.utils

import com.pubnub.api.createJsonElement
import com.pubnub.api.models.consumer.history.HistoryMessageType
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.utils.PatchValue
import com.pubnub.chat.types.ChannelType
import com.pubnub.kmp.CustomObject

open class BaseTest {
    internal val id = "testId"

    internal fun createPnFetchMessagesResult(
        channelId: String,
        user1: String,
        message1: String,
        timetoken1: Long,
        user2: String,
        message2: String,
        timetoken2: Long
    ) = PNFetchMessagesResult(
        mapOf(
            channelId to listOf(
                PNFetchMessageItem(
                    user1, createJsonElement(mapOf("type" to "text", "text" to message1)), null,
                    timetoken1, null, HistoryMessageType.Message, null
                ),
                PNFetchMessageItem(
                    user2, createJsonElement(mapOf("type" to "text", "text" to message2, "files" to null)), null,
                    timetoken2, null, HistoryMessageType.Message, null
                ),
            )
        ),
        null
    )

    internal fun getPNChannelMetadataResult(
        updatedId: String? = null,
        updatedName: String = "",
        updatedDescription: String = "",
        updatedCustom: Map<String, Any?>? = null,
        updatedUpdated: String = "",
        updatedType: String = ChannelType.GROUP.toString().lowercase(),
        updatedStatus: String = "",
    ): PNChannelMetadataResult {
        val actualId = updatedId ?: id
        val pnChannelMetadata = PNChannelMetadata(
            id = actualId,
            name = PatchValue.of(updatedName),
            description = PatchValue.of(updatedDescription),
            custom = PatchValue.of(updatedCustom),
            updated = PatchValue.of(updatedUpdated),
            eTag = PatchValue.of("updatedETag"),
            type = PatchValue.of(updatedType),
            status = PatchValue.of(updatedStatus)
        )
        return PNChannelMetadataResult(status = 200, data = pnChannelMetadata)
    }
}

internal expect fun CustomObject.get(key: String): Any?
