package com.pubnub.chat.internal.message

import com.pubnub.api.JsonElement
import com.pubnub.api.PubNubException
import com.pubnub.api.asMap
import com.pubnub.api.endpoints.message_actions.RemoveMessageAction
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.message_actions.PNAddMessageActionResult
import com.pubnub.api.models.consumer.message_actions.PNGetMessageActionsResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.METADATA_MENTIONED_USERS
import com.pubnub.chat.internal.METADATA_QUOTED_MESSAGE
import com.pubnub.chat.internal.METADATA_REFERENCED_CHANNELS
import com.pubnub.chat.internal.METADATA_TEXT_LINKS
import com.pubnub.chat.internal.THREAD_ROOT_ID
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.internal.error.PubNubErrorMessage.CANNOT_STREAM_MESSAGE_UPDATES_ON_EMPTY_LIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.THIS_MESSAGE_HAS_NOT_BEEN_DELETED
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.internal.util.logWarnAndReturnException
import com.pubnub.chat.internal.util.pnError
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
import org.lighthousegames.logging.logging
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
                if (acc.value.actionTimetoken > entry.value.actionTimetoken) {
                    acc
                } else {
                    entry
                }
            }
            return lastEdit.key
        }

    override val deleted: Boolean
        get() = getDeleteAction() != null

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

    override val reactions
        get() = actions?.get(com.pubnub.chat.types.MessageActionType.REACTIONS.toString()) ?: emptyMap()

    override val textLinks: List<TextLink>? get() = (
        meta?.get(
            METADATA_TEXT_LINKS
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
            var updatedActions: Map<String, Map<String, List<PNFetchMessageItem.Action>>> = mapOf()
            return chat.pubNub.addMessageAction(
                channelId,
                PNMessageAction(
                    type,
                    type,
                    timetoken
                )
            ).thenAsync {
                deleteThread(soft)
            }.thenAsync {
                chat.pubNub.getMessageActions(channel = channelId, page = PNBoundedPage(end = timetoken))
            }.then { pnGetMessageActionsResult: PNGetMessageActionsResult ->
                val messageActionsForMessage: List<PNMessageAction> =
                    pnGetMessageActionsResult.actions.filter { it.messageTimetoken == timetoken }
                // update actions map
                messageActionsForMessage.forEach { pnMessageAction ->
                    updatedActions = assignAction(updatedActions, pnMessageAction)
                }
            }.then {
                copyWithActions(updatedActions)
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
            if (channel == null) {
                log.pnError(PubNubErrorMessage.CHANNEL_NOT_EXIST)
            }
            ChatImpl.pinMessageToChannel(chat.pubNub, this, channel).then {
                ChannelImpl.fromDTO(chat, it.data)
            }
        }
    }

    override fun report(reason: String): PNFuture<PNPublishResult> {
        val channelId = "$INTERNAL_MODERATION_PREFIX$channelId"
        return chat.emitEvent(
            channelId,
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

    override fun removeThread() = chat.removeThreadChannel(chat, this)

    override fun toggleReaction(reaction: String): PNFuture<Message> {
        val existingReaction = reactions[reaction]?.find {
            it.uuid == chat.currentUser.id
        }
        val messageAction =
            PNMessageAction(com.pubnub.chat.types.MessageActionType.REACTIONS.toString(), reaction, timetoken)
        val newActions = if (existingReaction != null) {
            chat.pubNub.removeMessageAction(channelId, timetoken, existingReaction.actionTimetoken.toLong())
                .then { filterAction(actions, messageAction) }
        } else {
            chat.pubNub.addMessageAction(channelId, messageAction)
                .then { assignAction(actions, it) }
        }
        return newActions.then { copyWithActions(it) }
    }

    override fun <M : Message> streamUpdates(callback: (message: M) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this as M)) {
            callback(it.first())
        }
    }

    override fun restore(): PNFuture<Message> {
        if (!deleted) {
            return PubNubException(THIS_MESSAGE_HAS_NOT_BEEN_DELETED).logWarnAndReturnException(log).asFuture()
        }

        var updatedActions: Map<String, Map<String, List<PNFetchMessageItem.Action>>> = mapOf()
        val deleteAction: PNFetchMessageItem.Action = getDeleteAction()!!
        val removeMessageAction = removeMessageAction(deleteAction.actionTimetoken)

        return removeMessageAction.then {
            // from actions map remove entries associated with delete operations
            updatedActions = actions!!.filterNot { it.key == chat.deleteMessageActionName }
        }.thenAsync {
            // get messageAction for all messages in channel
            chat.pubNub.getMessageActions(channel = channelId, page = PNBoundedPage(end = timetoken))
        }.then { pnGetMessageActionsResult: PNGetMessageActionsResult ->
            // getMessageAction assigned to this message
            val messageActionsForMessage = pnGetMessageActionsResult.actions.filter { it.messageTimetoken == timetoken }

            // update actions map
            messageActionsForMessage.forEach { pnMessageAction ->
                updatedActions = assignAction(updatedActions, pnMessageAction)
            }
            updatedActions
        }.thenAsync {
            chat.restoreThreadChannel(this)
        }.then { pnMessageAction: PNMessageAction? ->
            // update actions map
            pnMessageAction?.let { updatedActions = assignAction(updatedActions, pnMessageAction) }
        }.then {
            copyWithActions(updatedActions)
        }
    }

    private fun removeMessageAction(deleteActionTimetoken: Long): RemoveMessageAction {
        val removeMessageAction = chat.pubNub.removeMessageAction(
            channel = channelId,
            messageTimetoken = timetoken,
            actionTimetoken = deleteActionTimetoken
        )
        return removeMessageAction
    }

    private fun getDeleteAction(): PNFetchMessageItem.Action? {
        return actions?.get(chat.deleteMessageActionName)?.get(chat.deleteMessageActionName)?.first()
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

    companion object {
        private val log = logging()

        fun <T : Message> streamUpdatesOn(
            messages: Collection<T>,
            callback: (messages: Collection<T>) -> Unit,
        ): AutoCloseable {
            if (messages.isEmpty()) {
                log.pnError(CANNOT_STREAM_MESSAGE_UPDATES_ON_EMPTY_LIST)
            }
            var latestMessages = messages
            val chat = messages.first().chat
            val listener = createEventListener(chat.pubNub, onMessageAction = { _, event ->
                val message =
                    latestMessages.find { it.timetoken == event.messageAction.messageTimetoken }
                        ?: return@createEventListener
                if (message.channelId != event.channel) {
                    return@createEventListener
                }
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
            return this?.asMap()?.get(METADATA_MENTIONED_USERS)?.let { PNDataEncoder.decode(it) }
        }

        internal fun JsonElement?.extractReferencedChannels(): MessageReferencedChannels? {
            return this?.asMap()?.get(METADATA_REFERENCED_CHANNELS)?.let { PNDataEncoder.decode(it) }
        }

        internal fun JsonElement?.extractQuotedMessage(): QuotedMessage? {
            return this?.asMap()?.get(METADATA_QUOTED_MESSAGE)?.let { PNDataEncoder.decode(it) }
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
