package com.pubnub.kmp

import com.pubnub.api.JsonElement
import com.pubnub.api.asMap
import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.message_actions.PNAddMessageActionResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannels
import com.pubnub.kmp.types.TextLink

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
    val referencedChannels: MessageReferencedChannels? = null,
    val textLinks: TextLink? = null, //todo
    val quotedMessage: Message? = null, // todo
) {
    val text: String
        get() {
            val edits = actions?.get(chat.editMessageActionName) ?: return content.text
            val flatEdits = edits.mapValues { it.value.first() }
            val lastEdit = flatEdits.entries.reduce { acc, entry ->
                if (acc.value.actionTimetoken.toLong() > entry.value.actionTimetoken.toLong()) {
                    acc
                } else {
                    entry
                }
            }
            return lastEdit.key
        }

    val deleted: Boolean
        get() = actions?.get(chat.deleteMessageActionName)?.get(chat.deleteMessageActionName)?.isNotEmpty() ?: false

    val hasThread: Boolean
        get() {
            if (actions?.containsKey(THREAD_ROOT_ID) != true) {
                return false
            }
            return actions[THREAD_ROOT_ID]?.entries?.firstOrNull()?.value?.isNotEmpty() ?: false
        }

    val type = EventContent.TextMessageContent.serializer().descriptor.serialName // = "text"

    val files: List<File>
        get() = content.files ?: emptyList()

    fun editText(newText: String): PNFuture<Message> {
        val type = chat.editMessageActionName
        return chat.pubNub.addMessageAction(
            channelId, PNMessageAction(
                type, newText, timetoken
            )
        ).then { actionResult: PNAddMessageActionResult ->
            val actions = assignAction(actionResult)
            this.copy(actions = actions)
        }
    }

    internal fun assignAction(actionResult: PNAddMessageActionResult): Map<String, Map<String, List<Action>>> {
        val type = actionResult.type
        val newActions = actions?.toMutableMap() ?: mutableMapOf()
        val actionValue = (newActions[type]?.toMutableMap() ?: mutableMapOf()).also {
            newActions[type] = it
        }
        val valueList = (actionValue[actionResult.value]?.toMutableList() ?: mutableListOf()).also {
            actionValue[actionResult.value] = it
        }
        if (valueList.any { it.actionTimetoken.toLong() == actionResult.actionTimetoken }) {
            return newActions
        }
        valueList.add(Action(actionResult.uuid!!, actionResult.actionTimetoken.toString()))
        return newActions
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
                mentionedUsers = pnMessageResult.userMetadata.extractMentionedUsers(),
                referencedChannels = pnMessageResult.userMetadata.extractReferencedChannels()
            )
        }

        fun fromDTO(chat: Chat, messageItem: PNFetchMessageItem, channelId: String): Message {
            val eventContent = try {
                messageItem.message.asString()?.let { text ->
                    EventContent.TextMessageContent(text, null)
                } ?: PNDataEncoder.decode(messageItem.message)
            } catch (e: Exception) {
                EventContent.UnknownMessageFormat(messageItem.message)
            }

            return Message(
                chat,
                messageItem.timetoken!!,
                eventContent,
                channelId,
                messageItem.uuid!!,
                messageItem.actions,
                messageItem.meta?.decode()?.let { it as Map<String, Any>? },
                mentionedUsers = messageItem.meta.extractMentionedUsers(),
                referencedChannels = messageItem.meta.extractReferencedChannels()
            )
        }

        private fun JsonElement?.extractMentionedUsers(): MessageMentionedUsers? {
            if (this == null) {
                return null
            }
            return asMap()?.get("mentionedUsers")?.let { PNDataEncoder.decode(it) }
        }

        private fun JsonElement?.extractReferencedChannels(): MessageReferencedChannels? {
            if (this == null) {
                return null
            }
            return asMap()?.get("referencedChannels")?.let { PNDataEncoder.decode(it) }
        }
    }
}
