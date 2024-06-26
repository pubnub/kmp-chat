package com.pubnub.kmp.message

import com.pubnub.api.JsonElement
import com.pubnub.api.asMap
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.message_actions.PNAddMessageActionResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.Channel
import com.pubnub.kmp.ChatImpl
import com.pubnub.kmp.Message
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.ThreadChannel
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.MessageActionType
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannels
import com.pubnub.kmp.types.QuotedMessage
import com.pubnub.kmp.types.TextLink
import kotlinx.serialization.ExperimentalSerializationApi

private const val THREAD_ROOT_ID = "threadRootId"
private const val INTERNAL_ADMIN_CHANNEL = "PUBNUB_INTERNAL_ADMIN_CHANNEL"

typealias Actions = Map<String, Map<String, List<PNFetchMessageItem.Action>>>

abstract class BaseMessage<T : Message>(
    private val chat: ChatImpl,
    override val timetoken: Long,
    override val content: EventContent.TextMessageContent,
    override val channelId: String,
    override val userId: String,
    override val actions: Map<String, Map<String, List<PNFetchMessageItem.Action>>>? = null,
    override val meta: Map<String, Any>? = null,
    override val mentionedUsers: MessageMentionedUsers? = null,
    override val referencedChannels: MessageReferencedChannels? = null,
    override val quotedMessage: QuotedMessage? = null,
) : Message {
    override val text: String
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

    override val deleted: Boolean
        get() = actions?.get(chat.deleteMessageActionName)?.get(chat.deleteMessageActionName)?.isNotEmpty() ?: false

    override val hasThread: Boolean
        get() {
            if (actions?.containsKey(THREAD_ROOT_ID) != true) {
                return false
            }
            return actions?.get(THREAD_ROOT_ID)?.entries?.firstOrNull()?.value?.isNotEmpty() ?: false
        }

    @OptIn(ExperimentalSerializationApi::class)
    override val type = EventContent.TextMessageContent.serializer().descriptor.serialName // = "text"

    override val files: List<File>
        get() = content.files ?: emptyList()

    override val reactions get() = actions?.get(MessageActionType.REACTIONS.toString()) ?: emptyMap()

    override val textLinks: List<TextLink>? get() = (meta?.get("textLinks") as? List<Any>)?.let { textLinksList: List<Any> ->
        textLinksList.filterIsInstance<Map<*,*>>().map { textLinkItem: Map<*, *> ->
            TextLink(textLinkItem["startIndex"] as Int, textLinkItem["endIndex"] as Int, textLinkItem["link"] as String)
        }
    }

    override fun hasUserReaction(reaction: String): Boolean {
        return reactions[reaction]?.any { it.uuid == chat.pubNub.configuration.userId.value } ?: false
    }

    override fun editText(newText: String): PNFuture<Message> {
        val type = chat.editMessageActionName
        return chat.pubNub.addMessageAction(
            channelId, PNMessageAction(
                type, newText, timetoken
            )
        ).then { actionResult: PNAddMessageActionResult ->
            val actions: Actions = assignAction(actionResult)
            this.copyWithActions(actions)
        }
    }

    override fun delete(soft: Boolean, preserveFiles: Boolean): PNFuture<Message?> {
        val type = chat.deleteMessageActionName
        if (soft) {
            return chat.pubNub.addMessageAction(channelId, PNMessageAction(
                type, type, timetoken
            )
            ).then { it: PNAddMessageActionResult ->
                val actions = assignAction(it)
                copyWithActions(actions)
            }.alsoAsync {
                deleteThread(soft)
            }
        } else {
            val previousTimetoken = timetoken - 1
            return chat.pubNub.deleteMessages(
                listOf(channelId),
                previousTimetoken,
                timetoken
            ).alsoAsync {
                deleteThread(soft)
            }.alsoAsync {
                if (files.isNotEmpty() && !preserveFiles) {
                    files.map { file ->
                        chat.pubNub.deleteFile(channelId, file.name, file.id)
                    }.awaitAll()
                } else {
                    Unit.asFuture()
                }
            }.then {
                null
            }
        }
    }

    override fun getThread() = chat.getThreadChannel(this)

    override fun forward(channelId: String): PNFuture<PNPublishResult> = chat.forwardMessage(this, channelId)

    override fun pin(): PNFuture<Channel> {
        return chat.getChannel(channelId).thenAsync { channel ->
            ChatImpl.pinMessageToChannel(chat.pubNub, this, channel!!).then {
                ChannelImpl.fromDTO(chat, it.data!!)
            }
        }
    }

    override fun report(reason: String): PNFuture<PNPublishResult> {
        return chat.emitEvent(
            INTERNAL_ADMIN_CHANNEL,
            EventContent.Report(
                text,
                reason,
                timetoken,
                channelId,
                userId
            ),
        )
    }

    override fun createThread(): PNFuture<ThreadChannel> = ChatImpl.createThreadChannel(chat, this)

    override fun removeThread() = ChatImpl.removeThreadChannel(chat, this)

    private fun deleteThread(soft: Boolean): PNFuture<Unit> {
        if (hasThread) {
            return getThread().thenAsync {
                it.delete(soft)
            }.then { Unit }
        }
        return Unit.asFuture()
    }

    private fun assignAction(actionResult: PNAddMessageActionResult): Map<String, Map<String, List<PNFetchMessageItem.Action>>> {
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
        valueList.add(PNFetchMessageItem.Action(actionResult.uuid!!, actionResult.actionTimetoken.toString()))
        return newActions
    }

    internal fun asQuotedMessage() : QuotedMessage {
        return QuotedMessage(
            timetoken,
            text,
            userId
        )
    }

    internal abstract fun copyWithActions(actions: Actions): T

    companion object {
        internal fun JsonElement?.extractMentionedUsers(): MessageMentionedUsers? {
            if (this == null) {
                return null
            }
            return asMap()?.get("mentionedUsers")?.let { PNDataEncoder.decode(it) }
        }

        internal fun JsonElement?.extractReferencedChannels(): MessageReferencedChannels? {
            if (this == null) {
                return null
            }
            return asMap()?.get("referencedChannels")?.let { PNDataEncoder.decode(it) }
        }
    }
}