package com.pubnub.chat.internal.message

import com.pubnub.api.JsonElement
import com.pubnub.api.PubNubException
import com.pubnub.api.asMap
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.message_actions.PNAddMessageActionResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_ADMIN_CHANNEL
import com.pubnub.chat.internal.THREAD_ROOT_ID
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import kotlinx.serialization.ExperimentalSerializationApi
import tryInt

typealias Actions = Map<String, Map<String, List<PNFetchMessageItem.Action>>>

abstract class BaseMessage<T : Message>(
    override val chat: ChatInternal,
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

    override val reactions get() = actions?.get(com.pubnub.chat.types.MessageActionType.REACTIONS.toString()) ?: emptyMap()

    override val textLinks: List<TextLink>? get() = (
        meta?.get(
            "textLinks"
        ) as? List<Any>
    )?.let { textLinksList: List<Any> ->
        textLinksList.filterIsInstance<Map<*, *>>().map { textLinkItem: Map<*, *> ->
            TextLink(
                textLinkItem["startIndex"].tryInt()!!,
                textLinkItem["endIndex"].tryInt()!!,
                textLinkItem["link"] as String
            )
        }
    }

    override fun hasUserReaction(reaction: String): Boolean {
        return reactions[reaction]?.any { it.uuid == chat.pubNub.configuration.userId.value } ?: false
    }

    override fun editText(newText: String): PNFuture<Message> {
        val type = chat.editMessageActionName
        return chat.pubNub.addMessageAction(
            channelId,
            PNMessageAction(
                type,
                newText,
                timetoken
            )
        ).then { actionResult: PNAddMessageActionResult ->
            val actions: Actions = assignAction(actions, actionResult)
            this.copyWithActions(actions)
        }
    }

    override fun delete(soft: Boolean, preserveFiles: Boolean): PNFuture<Message?> {
        val type = chat.deleteMessageActionName
        if (soft) {
            return chat.pubNub.addMessageAction(
                channelId,
                PNMessageAction(
                    type,
                    type,
                    timetoken
                )
            ).then { it: PNAddMessageActionResult ->
                val actions = assignAction(actions, it)
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

    override fun toggleReaction(reaction: String): PNFuture<Message> {
        val existingReaction = reactions[reaction]?.find {
            it.uuid == chat.currentUser.id
        }
        val messageAction = PNMessageAction(com.pubnub.chat.types.MessageActionType.REACTIONS.toString(), reaction, timetoken)
        val newActions = if (existingReaction != null) {
            chat.pubNub.removeMessageAction(channelId, timetoken, existingReaction.actionTimetoken.toLong())
                .then { filterAction(actions, messageAction) }
        } else {
            chat.pubNub.addMessageAction(channelId, messageAction)
                .then { assignAction(actions, it) }
        }
        return newActions.then { copyWithActions(it) }
    }

    private fun deleteThread(soft: Boolean): PNFuture<Unit> {
        if (hasThread) {
            return getThread().thenAsync {
                it.delete(soft)
            }.then { Unit }
        }
        return Unit.asFuture()
    }

    internal fun asQuotedMessage(): QuotedMessage {
        return QuotedMessage(
            timetoken,
            text,
            userId
        )
    }

    internal abstract fun copyWithActions(actions: Actions): T

    override fun <M : Message> streamUpdates(callback: (message: M) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this as M)) {
            callback(it.first())
        }
    }

    companion object {
        fun <T : Message> streamUpdatesOn(
            messages: Collection<T>,
            callback: (messages: Collection<T>) -> Unit,
        ): AutoCloseable {
            if (messages.isEmpty()) {
                throw PubNubException("Cannot stream message updates on an empty list")
            }
            var latestMessages = messages
            val chat = messages.first().chat
            val listener = createEventListener(chat.pubNub, onMessageAction = { _, event ->
                val message =
                    latestMessages.find { it.timetoken == event.messageAction.messageTimetoken } ?: return@createEventListener
                if (message.channelId != event.channel) return@createEventListener
                val actions = if (event.event == "added") {
                    assignAction(
                        message.actions,
                        event.messageAction
                    )
                } else {
                    filterAction(
                        message.actions,
                        event.messageAction
                    )
                }
                val newMessage = (message as BaseMessage<T>).copyWithActions(actions)
                latestMessages = latestMessages.map {
                    if (it.timetoken == newMessage.timetoken) {
                        newMessage
                    } else {
                        it
                    }
                }
                callback(latestMessages)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(
                messages.map { it.channelId }.toSet()
            )
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        internal fun JsonElement?.extractMentionedUsers(): MessageMentionedUsers? {
            // todo create "mentionedUsers" constant and reuse across SDK
            return this?.asMap()?.get("mentionedUsers")?.let { PNDataEncoder.decode(it) }
        }

        internal fun JsonElement?.extractReferencedChannels(): MessageReferencedChannels? {
            // todo create "referencedChannels" constant and reuse across SDK
            return this?.asMap()?.get("referencedChannels")?.let { PNDataEncoder.decode(it) }
        }

        internal fun JsonElement?.extractQuotedMessage(): QuotedMessage? {
            // todo create "quotedMessage" constant and reuse across SDK
            return this?.asMap()?.get("quotedMessage")?.let { PNDataEncoder.decode(it) }
        }

        internal fun assignAction(actions: Actions?, actionResult: PNMessageAction): Actions {
            val type = actionResult.type
            val newActions = actions?.toMutableMap() ?: mutableMapOf()
            val actionValue = (newActions[type]?.toMutableMap() ?: mutableMapOf()).also {
                newActions[type] = it
            }
            val valueList = (actionValue[actionResult.value]?.toMutableList() ?: mutableListOf()).also {
                actionValue[actionResult.value] = it
            }
            if (valueList.any { it.actionTimetoken == actionResult.actionTimetoken }) {
                return newActions
            }
            valueList.add(PNFetchMessageItem.Action(actionResult.uuid!!, actionResult.actionTimetoken!!))
            return newActions
        }

        internal fun filterAction(actions: Actions?, action: PNMessageAction): Actions {
            return buildMap {
                actions?.entries?.forEach { entry ->
                    put(
                        entry.key,
                        buildMap {
                            entry.value.forEach { innerEntry ->
                                if (entry.key == action.type && innerEntry.key == action.value) {
                                    put(
                                        innerEntry.key,
                                        innerEntry.value.filter {
                                            it.actionTimetoken != action.actionTimetoken || it.uuid != action.uuid
                                        }
                                    )
                                } else {
                                    put(innerEntry.key, innerEntry.value)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
