package com.pubnub.kmp

import com.pubnub.api.asMap
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannel

private const val THREAD_ROOT_ID = "threadRootId"

data class Message(
    private val chat: Chat,
    val timetoken: Long,
    val content: EventContent.TextMessageContent,
    val channelId: String,
    val userId: String,
    val actions: Map<String, Map<String, List<Action>>>? = null,
    val meta: Map<String, Any>? = null,
    val mentionedUsers: MessageMentionedUsers? = null,
    val referencedChannels: MessageReferencedChannel? = null,
) {
    val text: String
        get() = content.text // todo implement Message.text() method from TS

    public val hasThread: Boolean
        get() {
            if (actions?.containsKey(THREAD_ROOT_ID) != true) {
                return false
            }
            return actions[THREAD_ROOT_ID]?.entries?.firstOrNull()?.value?.isNotEmpty() ?: false
        }

    companion object {
        fun fromDTO(chat: Chat, pnMessageResult: PNMessageResult): Message {
            return Message(
                chat,
                pnMessageResult.timetoken!!,
                PNDataEncoder.decode<EventContent>(pnMessageResult.message) as EventContent.TextMessageContent,
                pnMessageResult.channel,
                pnMessageResult.publisher!!,
                meta = pnMessageResult.userMetadata?.decode() as? Map<String, Any>,
                mentionedUsers = pnMessageResult.userMetadata?.asMap()?.get("mentionedUsers")?.let { PNDataEncoder.decode(it) },
                referencedChannels = pnMessageResult.userMetadata?.asMap()?.get("referencedChannels")?.let { PNDataEncoder.decode(it) }
            )
        }
    }
}
