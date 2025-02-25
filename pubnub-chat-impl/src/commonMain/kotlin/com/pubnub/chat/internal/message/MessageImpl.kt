package com.pubnub.chat.internal.message

import co.touchlab.kermit.Logger
import com.pubnub.api.JsonElement
import com.pubnub.api.PubNubError
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessageItem.Action
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.internal.ChatImpl.Companion.getThreadId
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.MESSAGE_THREAD_ID_PREFIX
import com.pubnub.chat.internal.THREAD_ROOT_ID
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.internal.defaultGetMessageResponseBody
import com.pubnub.chat.internal.error.PubNubErrorMessage.ONLY_ONE_LEVEL_OF_THREAD_NESTING_IS_ALLOWED
import com.pubnub.chat.internal.error.PubNubErrorMessage.THREAD_FOR_THIS_MESSAGE_ALREADY_EXISTS
import com.pubnub.chat.internal.error.PubNubErrorMessage.YOU_CAN_NOT_CREATE_THREAD_ON_DELETED_MESSAGES
import com.pubnub.chat.internal.util.logErrorAndReturnException
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync

data class MessageImpl(
    override val chat: ChatInternal,
    override val timetoken: Long,
    override val content: EventContent.TextMessageContent,
    override val channelId: String,
    override val userId: String,
    override val actions: Map<String, Map<String, List<Action>>>? = null,
    val metaInternal: JsonElement? = null,
    override val error: PubNubError? = null,
) : Message, BaseMessageImpl<Message, Channel>(
        chat = chat,
        timetoken = timetoken,
        content = content,
        channelId = channelId,
        userId = userId,
        actions = actions,
        metaInternal = metaInternal,
        error = error,
    ) {
    override val hasThread: Boolean
        get() {
            return actions?.get(THREAD_ROOT_ID)?.values?.firstOrNull()?.isNotEmpty() ?: false
        }

    override fun getThread() = chat.getThreadChannel(this)

    override fun createThread(): PNFuture<ThreadChannel> {
        if (channelId.startsWith(MESSAGE_THREAD_ID_PREFIX)) {
            return log.logErrorAndReturnException(ONLY_ONE_LEVEL_OF_THREAD_NESTING_IS_ALLOWED).asFuture()
        }
        if (deleted) {
            return log.logErrorAndReturnException(YOU_CAN_NOT_CREATE_THREAD_ON_DELETED_MESSAGES).asFuture()
        }

        val threadChannelId = getThreadId(channelId, timetoken)
        return chat.getChannel(threadChannelId).thenAsync { it: Channel? ->
            if (it != null) {
                return@thenAsync log.logErrorAndReturnException(THREAD_FOR_THIS_MESSAGE_ALREADY_EXISTS)
                    .asFuture()
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

    override fun removeThread(): PNFuture<Pair<PNRemoveMessageActionResult, ThreadChannel?>> = chat.removeThreadChannel(chat, this)

    override fun deleteThread(soft: Boolean): PNFuture<Unit> {
        if (hasThread) {
            return getThread().thenAsync {
                it.delete(soft)
            }.then { Unit }
        }
        return Unit.asFuture()
    }

    override fun copyWithActions(actions: Actions?): Message = copy(actions = actions)

    override fun copyWithContent(content: EventContent.TextMessageContent): Message = copy(content = content)

    override fun restore(): PNFuture<Message> {
        return super.restore().thenAsync { message ->
            // attempt to restore the thread channel related to this message if exists
            chat.restoreThreadChannel(this).then { addThreadRootIdMessageAction: PNMessageAction? ->
                // update actions map by adding THREAD_ROOT_ID if there is thread related to the message
                addThreadRootIdMessageAction?.let { notNullAction ->
                    val updatedActions = assignAction(message.actions, notNullAction)
                    (message as MessageImpl).copyWithActions(updatedActions)
                } ?: message
            }
        }
    }

    override fun pin(): PNFuture<Channel> {
        return pinInternal().then {
            ChannelImpl.fromDTO(chat, it.data)
        }
    }

    companion object {
        private val log = Logger.withTag("MessageImpl")

        internal fun fromDTO(chat: ChatInternal, pnMessageResult: PNMessageResult): Message {
            val content =
                chat.config.customPayloads?.getMessageResponseBody?.invoke(pnMessageResult.message, pnMessageResult.channel, ::defaultGetMessageResponseBody)
                    ?: defaultGetMessageResponseBody(pnMessageResult.message)
                    ?: EventContent.UnknownMessageFormat(pnMessageResult.message)
            return MessageImpl(
                chat = chat,
                timetoken = pnMessageResult.timetoken!!,
                content = content,
                channelId = pnMessageResult.channel,
                userId = pnMessageResult.publisher!!,
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
                chat = chat,
                timetoken = messageItem.timetoken!!,
                content = content,
                channelId = channelId,
                userId = messageItem.uuid!!,
                actions = messageItem.actions,
                metaInternal = messageItem.meta,
                error = messageItem.error,
            )
        }
    }
}
