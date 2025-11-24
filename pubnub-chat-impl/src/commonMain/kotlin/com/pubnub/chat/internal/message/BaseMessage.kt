package com.pubnub.chat.internal.message

import co.touchlab.kermit.Logger
import com.pubnub.api.JsonElement
import com.pubnub.api.PubNubError
import com.pubnub.api.asMap
import com.pubnub.api.decode
import com.pubnub.api.endpoints.message_actions.RemoveMessageAction
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.message_actions.PNAddMessageActionResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatImpl.Companion.getThreadId
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.MESSAGE_THREAD_ID_PREFIX
import com.pubnub.chat.internal.METADATA_MENTIONED_USERS
import com.pubnub.chat.internal.METADATA_QUOTED_MESSAGE
import com.pubnub.chat.internal.METADATA_REFERENCED_CHANNELS
import com.pubnub.chat.internal.METADATA_TEXT_LINKS
import com.pubnub.chat.internal.PUBNUB_INTERNAL_AUTOMODERATED
import com.pubnub.chat.internal.THREAD_ROOT_ID
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.internal.error.PubNubErrorMessage.AUTOMODERATED_MESSAGE_CANNOT_BE_EDITED
import com.pubnub.chat.internal.error.PubNubErrorMessage.CANNOT_STREAM_MESSAGE_UPDATES_ON_EMPTY_LIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.KEY_IS_NOT_VALID_INTEGER
import com.pubnub.chat.internal.error.PubNubErrorMessage.ONLY_ONE_LEVEL_OF_THREAD_NESTING_IS_ALLOWED
import com.pubnub.chat.internal.error.PubNubErrorMessage.THIS_MESSAGE_HAS_NOT_BEEN_DELETED
import com.pubnub.chat.internal.error.PubNubErrorMessage.THREAD_FOR_THIS_MESSAGE_ALREADY_EXISTS
import com.pubnub.chat.internal.error.PubNubErrorMessage.YOU_CAN_NOT_CREATE_THREAD_ON_DELETED_MESSAGES
import com.pubnub.chat.internal.isInternalModerator
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.internal.util.logErrorAndReturnException
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.types.EntityChange
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannel
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
    private val metaInternal: JsonElement? = null,
    override val error: PubNubError? = null,
) : Message {
    override val meta: Map<String, Any>? get() = metaInternal?.decode() as? Map<String, Any>
    override val quotedMessage: QuotedMessage? get() = metaInternal.extractQuotedMessage()
    override val mentionedUsers: MessageMentionedUsers? get() = metaInternal.extractMentionedUsers()
    override val referencedChannels: MessageReferencedChannels? get() = metaInternal.extractReferencedChannels()

    override val text: String
        get() {
            val edits = actions?.get(chat.editMessageActionName) ?: return content.text
            val flatEdits = edits.filterValues { it.isNotEmpty() }.mapValues { it.value.first() }
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
        get() = getDeleteActions() != null

    override val hasThread: Boolean
        get() {
            return actions?.get(THREAD_ROOT_ID)?.values?.firstOrNull()?.isNotEmpty() ?: false
        }

    @OptIn(ExperimentalSerializationApi::class)
    override val type = EventContent.TextMessageContent.serializer().descriptor.serialName // = "text"

    override val files: List<File>
        get() = content.files ?: emptyList()

    override val reactions: Map<String, List<PNFetchMessageItem.Action>>
        get() = actions?.get(chat.reactionsActionName) ?: emptyMap()

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
        if (this.meta?.containsKey(PUBNUB_INTERNAL_AUTOMODERATED) == true && !this.chat.currentUser.isInternalModerator) {
            return log.logErrorAndReturnException(AUTOMODERATED_MESSAGE_CANNOT_BE_EDITED).asFuture()
        }

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
            var updatedActions: Actions = actions ?: mapOf()
            return chat.pubNub.addMessageAction(
                channelId,
                PNMessageAction(
                    type,
                    type,
                    timetoken
                )
            ).then { addMessageActionResult: PNAddMessageActionResult ->
                // add action related to delete
                updatedActions = assignAction(updatedActions, addMessageActionResult)
            }.alsoAsync {
                deleteThread(soft)
            }.then {
                // deleteThread method deletes reaction related to thread from PN and here be want to remove this action from "actions" map
                updatedActions = updatedActions.filterNot { it.key == THREAD_ROOT_ID }
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
            ChatImpl.pinOrUnpinMessageToChannel(chat.pubNub, this, channel).then {
                ChannelImpl.fromDTO(chat, it.data)
            }
        }
    }

    override fun report(reason: String): PNFuture<PNPublishResult> {
        val reportChannelId = "$INTERNAL_MODERATION_PREFIX$channelId"
        return chat.emitEvent(
            reportChannelId,
            EventContent.Report(
                text,
                reason,
                timetoken,
                channelId,
                userId
            ),
        )
    }

    @Deprecated("Use `createThread(text, ...)` or `createThreadMessageDraft()` instead to create a thread by sending the first reply.`")
    override fun createThread(): PNFuture<ThreadChannel> {
        if (channelId.startsWith(MESSAGE_THREAD_ID_PREFIX)) {
            return log.logErrorAndReturnException(ONLY_ONE_LEVEL_OF_THREAD_NESTING_IS_ALLOWED).asFuture()
        }
        if (deleted) {
            return log.logErrorAndReturnException(YOU_CAN_NOT_CREATE_THREAD_ON_DELETED_MESSAGES).asFuture()
        }

        val threadChannelId =
            getThreadId(channelId, timetoken)
        return chat.getChannel(threadChannelId).thenAsync { it: Channel? ->
            if (it != null) {
                return@thenAsync log.logErrorAndReturnException(THREAD_FOR_THIS_MESSAGE_ALREADY_EXISTS).asFuture()
            }
            ThreadChannelImpl(
                this,
                chat,
                description = "Thread on channel $channelId with message timetoken $timetoken",
                id = threadChannelId,
                threadCreated = false
            ).asFuture()
        }
    }

    override fun createThread(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        quotedMessage: Message?,
        files: List<InputFile>?,
        usersToMention: Collection<String>?,
        customPushData: Map<String, String>?
    ): PNFuture<ThreadChannel> {
        @Suppress("DEPRECATION")
        return createThread().alsoAsync {
            it.sendText(
                text, meta, shouldStore, usePost, ttl, quotedMessage, files, usersToMention, customPushData
            )
        }
    }

    override fun removeThread(): PNFuture<Pair<PNRemoveMessageActionResult, Channel?>> = chat.removeThreadChannel(chat, this)

    override fun toggleReaction(reaction: String): PNFuture<Message> {
        val existingReaction = reactions[reaction]?.find {
            it.uuid == chat.currentUser.id
        }
        val messageAction =
            PNMessageAction(chat.reactionsActionName, reaction, timetoken).apply {
                actionTimetoken = existingReaction?.actionTimetoken
                uuid = chat.currentUser.id
            }
        val newActions = if (existingReaction != null) {
            chat.pubNub.removeMessageAction(channelId, timetoken, existingReaction.actionTimetoken)
                .then { filterAction(actions, messageAction) }
        } else {
            chat.pubNub.addMessageAction(channelId, messageAction)
                .then { assignAction(actions, it) }
        }
        return newActions.then { copyWithActions(it) }
    }

    override fun <M : Message> streamUpdates(callback: (message: M) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this as M)) { messages: Collection<M> ->
            callback(messages.first())
        }
    }

    override fun restore(): PNFuture<Message> {
        val deleteActions: List<PNFetchMessageItem.Action> = getDeleteActions()
            ?: return this.also { log.w(THIS_MESSAGE_HAS_NOT_BEEN_DELETED) }.asFuture()

        var updatedActions: Actions? = actions?.filterNot { it.key == chat.deleteMessageActionName }

        return deleteActions
            .map { removeMessageAction(it.actionTimetoken) }
            .awaitAll()
            .thenAsync {
                // attempt to restore the thread channel related to this message if exists
                chat.restoreThreadChannel(this)
            }.then { addThreadRootIdMessageAction: PNMessageAction? ->
                // update actions map by adding THREAD_ROOT_ID if there is thread related to the message
                addThreadRootIdMessageAction?.let { notNullAction ->
                    updatedActions = assignAction(updatedActions, notNullAction)
                }
                copyWithActions(updatedActions)
            }
    }

    private fun removeMessageAction(deleteActionTimetoken: Long): RemoveMessageAction {
        return chat.pubNub.removeMessageAction(
            channel = channelId,
            messageTimetoken = timetoken,
            actionTimetoken = deleteActionTimetoken
        )
    }

    private fun getDeleteActions(): List<PNFetchMessageItem.Action>? {
        return actions?.get(chat.deleteMessageActionName)?.get(chat.deleteMessageActionName)
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

    internal abstract fun copyWithActions(actions: Actions?): T

    internal abstract fun copyWithContent(content: EventContent.TextMessageContent): T

    companion object {
        private val log = Logger.withTag("BaseMessageImpl")

        fun <T : Message> streamUpdatesOn(
            messages: Collection<T>,
            callback: (messages: Collection<T>) -> Unit,
        ): AutoCloseable {
            return streamUpdatesOnInternal(messages) { _, latestMessages ->
                callback(latestMessages)
            }
        }

        fun <T : Message> streamUpdatesOnWithEntityChange(
            messages: Collection<T>,
            callback: (change: EntityChange<T>) -> Unit,
        ): AutoCloseable {
            return streamUpdatesOnInternal(messages) { updatedMessage, _ ->
                callback(EntityChange.Updated(updatedMessage))
            }
        }

        private fun <T : Message> streamUpdatesOnInternal(
            messages: Collection<T>,
            callback: (updatedMessage: T, latestMessages: Collection<T>) -> Unit,
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
                callback(newMessage, latestMessages)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(
                messages.map { it.channelId }.toSet()
            )
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        internal fun JsonElement?.extractMentionedUsers(): MessageMentionedUsers? {
            return this?.asMap()?.get(METADATA_MENTIONED_USERS)?.let {
                // Json doesn't support integers as key and for some reason decode method doesn't automatically convert string keys to integers even
                // though MessageMentionedUsers = Map<Int, MessageMentionedUser>
                val decodeMap: Map<String, MessageMentionedUser> = PNDataEncoder.decode<Map<String, MessageMentionedUser>>(it)
                // manually convert key as string to int
                decodeMap.mapKeys { (key, _) -> key.toIntOrNull() ?: throw IllegalArgumentException(KEY_IS_NOT_VALID_INTEGER) }
            }
        }

        internal fun JsonElement?.extractReferencedChannels(): MessageReferencedChannels? {
            return this?.asMap()?.get(METADATA_REFERENCED_CHANNELS)?.let {
                // Json doesn't support integers as key and for some reason decode method doesn't automatically convert string keys to integers even
                // though MessageReferencedChannels = Map<Int, MessageReferencedChannel>
                val decodeMap: Map<String, MessageReferencedChannel> = PNDataEncoder.decode<Map<String, MessageReferencedChannel>>(it)
                // manually convert key as string to int
                decodeMap.mapKeys { (key, _) -> key.toIntOrNull() ?: throw IllegalArgumentException(KEY_IS_NOT_VALID_INTEGER) }
            }
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
                    val actionMap = buildMap {
                        entry.value.forEach { innerEntry ->
                            if (entry.key == action.type && innerEntry.key == action.value) {
                                val actionList = innerEntry.value.filter {
                                    it.actionTimetoken != action.actionTimetoken || it.uuid != action.uuid
                                }
                                if (actionList.isNotEmpty()) {
                                    put(innerEntry.key, actionList)
                                }
                            } else {
                                put(innerEntry.key, innerEntry.value)
                            }
                        }
                    }
                    if (actionMap.isNotEmpty()) {
                        put(entry.key, actionMap)
                    }
                }
            }
        }
    }
}
