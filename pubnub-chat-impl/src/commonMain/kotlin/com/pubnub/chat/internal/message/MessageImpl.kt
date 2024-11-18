package com.pubnub.chat.internal.message

import com.pubnub.api.JsonElement
import com.pubnub.api.PubNubError
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.chat.Message
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.defaultGetMessageResponseBody
import com.pubnub.chat.types.EventContent

data class MessageImpl(
    override val chat: ChatInternal,
    override val timetoken: Long,
    override val content: EventContent.TextMessageContent,
    override val channelId: String,
    override val userId: String,
    override val actions: Map<String, Map<String, List<Action>>>? = null,
    val metaInternal: JsonElement? = null,
    override val error: PubNubError? = null,
) : BaseMessage<Message>(
        chat = chat,
        timetoken = timetoken,
        content = content,
        channelId = channelId,
        userId = userId,
        actions = actions,
        metaInternal = metaInternal,
        error = error
    ) {
    override fun copyWithActions(actions: Actions?): Message = copy(actions = actions)

    override fun copyWithContent(content: EventContent.TextMessageContent): Message = copy(content = content)

    companion object {
        internal fun fromDTO(chat: ChatInternal, pnMessageResult: PNMessageResult): Message {
            val content =
                chat.config.customPayloads?.getMessageResponseBody?.invoke(pnMessageResult.message, pnMessageResult.channel, ::defaultGetMessageResponseBody)
                    ?: defaultGetMessageResponseBody(pnMessageResult.message)
                    ?: EventContent.UnknownMessageFormat(pnMessageResult.message)
            return MessageImpl(
                chat,
                pnMessageResult.timetoken!!,
                content,
                pnMessageResult.channel,
                pnMessageResult.publisher!!,
                metaInternal = pnMessageResult.userMetadata,
                error = pnMessageResult.error,
            )
        }

        internal fun fromDTO(chat: ChatInternal, messageItem: PNFetchMessageItem, channelId: String): Message {
            val content =
                chat.config.customPayloads?.getMessageResponseBody?.invoke(messageItem.message, channelId, ::defaultGetMessageResponseBody)
                    ?: defaultGetMessageResponseBody(messageItem.message)
                    ?: EventContent.UnknownMessageFormat(messageItem.message)

            return MessageImpl(
                chat,
                messageItem.timetoken!!,
                content,
                channelId,
                messageItem.uuid!!,
                messageItem.actions,
                messageItem.meta,
                messageItem.error,
            )
        }
    }
}
