package com.pubnub.chat.internal.channel

import co.touchlab.kermit.Logger
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.utils.Clock
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.THREAD_ROOT_ID
import com.pubnub.chat.internal.defaultGetMessageResponseBody
import com.pubnub.chat.internal.error.PubNubErrorMessage.ERROR_HANDLING_ONMESSAGE_EVENT
import com.pubnub.chat.internal.error.PubNubErrorMessage.PARENT_CHANNEL_DOES_NOT_EXISTS
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.internal.message.ThreadMessageImpl
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.SendTextParams
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync

data class ThreadChannelImpl(
    override val parentMessage: Message,
    override val chat: ChatInternal,
    val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
    private var threadCreated: Boolean = true,
) : BaseChannel<ThreadChannel, ThreadMessage>(
        chat,
        clock,
        id,
        name,
        custom,
        description,
        updated,
        status,
        type,
        { chat, pnChannelMetadata ->
            fromDTO(chat, parentMessage, pnChannelMetadata)
        },
        { chat, pnMessageItem, channelId ->
            ThreadMessageImpl.fromDTO(chat, pnMessageItem, channelId, parentMessage.channelId)
        }
    ),
    ThreadChannel {
    override val parentChannelId: String
        get() = parentMessage.channelId

    override fun pinMessageToParentChannel(message: ThreadMessage) = pinOrUnpinMessageFromParentChannel(message)

    override fun unpinMessageFromParentChannel(): PNFuture<Channel> = pinOrUnpinMessageFromParentChannel(null)

    private fun pinOrUnpinMessageFromParentChannel(message: ThreadMessage?): PNFuture<Channel> {
        return chat.getChannel(parentChannelId).thenAsync { parentChannel ->
            if (parentChannel == null) {
                log.pnError(PARENT_CHANNEL_DOES_NOT_EXISTS)
            }
            ChatImpl.pinOrUnpinMessageToChannel(chat.pubNub, message, parentChannel).then {
                ChannelImpl.fromDTO(chat, it.data)
            }
        }
    }

    override fun update(
        name: String?,
        custom: CustomObject?,
        description: String?,
        status: String?,
        type: ChannelType?
    ): PNFuture<ThreadChannel> {
        return super.update(name, custom, description, status, type).then { channel ->
            ThreadChannelImpl(
                parentMessage = parentMessage,
                chat = chat,
                clock = clock,
                id = id,
                name = channel.name,
                custom = channel.custom,
                description = channel.description,
                updated = channel.updated,
                status = channel.status,
                type = channel.type,
                threadCreated = threadCreated
            )
        }
    }

    override fun getPinnedMessage(): PNFuture<ThreadMessage?> {
        return super.getPinnedMessage().then { message ->
            (message as? BaseMessage<*>)?.let {
                ThreadMessageImpl(
                    this@ThreadChannelImpl.chat,
                    this@ThreadChannelImpl.parentChannelId,
                    message.timetoken,
                    message.content,
                    message.channelId,
                    message.userId,
                    message.actions,
                    message.metaInternal,
                    message.error
                )
            }
        }
    }

    override fun getMessage(timetoken: Long): PNFuture<ThreadMessage?> {
        return super.getMessage(timetoken).then { message ->
            (message as? BaseMessage<*>)?.let {
                ThreadMessageImpl(
                    chat = chat,
                    parentChannelId = parentChannelId,
                    timetoken = message.timetoken,
                    content = message.content,
                    channelId = message.channelId,
                    userId = message.userId,
                    actions = message.actions,
                    metaInternal = message.metaInternal,
                    error = message.error
                )
            }
        }
    }

    override fun delete(): PNFuture<Unit> {
        return chat.removeThreadChannel(chat, parentMessage).then { it.second }
    }

    private fun createThreadAndSend(sendAction: () -> PNFuture<PNPublishResult>): PNFuture<PNPublishResult> {
        return (
            if (!threadCreated) {
                awaitAll(
                    chat.pubNub.setChannelMetadata(id, description = description),
                    chat.pubNub.addMessageAction(
                        parentMessage.channelId,
                        PNMessageAction(
                            THREAD_ROOT_ID,
                            id,
                            parentMessage.timetoken
                        )
                    )
                )
            } else {
                Unit.asFuture()
            }
        ).thenAsync {
            threadCreated = true
            sendAction()
        }
    }

    @Deprecated("Will be removed from SDK in the future", level = DeprecationLevel.WARNING)
    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        mentionedUsers: MessageMentionedUsers?,
        referencedChannels: MessageReferencedChannels?,
        textLinks: List<TextLink>?,
        quotedMessage: Message?,
        files: List<InputFile>?,
        customPushData: Map<String, String>?,
    ): PNFuture<PNPublishResult> {
        return createThreadAndSend {
            super.sendText(
                text,
                meta,
                shouldStore,
                usePost,
                ttl,
                mentionedUsers,
                referencedChannels,
                textLinks,
                quotedMessage,
                files,
                customPushData
            )
        }
    }

    override fun sendText(
        text: String,
        params: SendTextParams,
    ): PNFuture<PNPublishResult> {
        return createThreadAndSend {
            super.sendText(
                text = text,
                params = params,
            )
        }
    }

    @Deprecated("Use sendText(text, SendTextParams) instead", level = DeprecationLevel.WARNING)
    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        quotedMessage: Message?,
        files: List<InputFile>?,
        usersToMention: Collection<String>?,
        customPushData: Map<String, String>?,
    ): PNFuture<PNPublishResult> {
        return createThreadAndSend {
            @Suppress("DEPRECATION")
            super.sendText(
                text = text,
                meta = meta,
                shouldStore = shouldStore,
                usePost = usePost,
                ttl = ttl,
                quotedMessage = quotedMessage,
                files = files,
                usersToMention = usersToMention,
                customPushData = customPushData,
            )
        }
    }

    override fun emitUserMention(
        userId: String,
        timetoken: Long,
        text: String,
        customPushData: Map<String, String>?,
    ): PNFuture<PNPublishResult> {
        return chat.emitEvent(
            channelId = userId,
            payload = EventContent.Mention(messageTimetoken = timetoken, channel = id, parentChannel = parentChannelId),
            mergePayloadWith = getPushPayload(this, text, chat.config.pushNotifications, customPushData),
        )
    }

    @Deprecated(
        "Use onThreadMessageReceived() for properly-typed ThreadMessage objects.",
        ReplaceWith("onThreadMessageReceived(callback)"),
        level = DeprecationLevel.WARNING,
    )
    override fun onMessageReceived(callback: (Message) -> Unit): AutoCloseable {
        return super.onMessageReceived(callback)
    }

    override fun onThreadMessageReceived(callback: (ThreadMessage) -> Unit): AutoCloseable {
        val channelEntity = chat.pubNub.channel(id)
        val subscription = channelEntity.subscription()
        val listener = createEventListener(
            chat.pubNub,
            onMessage = { _, pnMessageResult ->
                if (pnMessageResult.publisher in chat.mutedUsersManager.mutedUsers) {
                    return@createEventListener
                }
                try {
                    if (
                        (
                            chat.config.customPayloads?.getMessageResponseBody?.invoke(
                                pnMessageResult.message,
                                pnMessageResult.channel,
                                ::defaultGetMessageResponseBody
                            )
                                ?: defaultGetMessageResponseBody(pnMessageResult.message)
                        ) == null
                    ) {
                        return@createEventListener
                    }
                    callback(ThreadMessageImpl.fromDTO(chat, pnMessageResult, parentChannelId))
                } catch (e: Exception) {
                    log.e(throwable = e) { ERROR_HANDLING_ONMESSAGE_EVENT }
                }
            },
        )
        subscription.addListener(listener)
        subscription.subscribe()
        return subscription
    }

    @Deprecated(
        "Use onThreadChannelUpdated() for properly-typed ThreadChannel objects.",
        ReplaceWith("onThreadChannelUpdated(callback)"),
        level = DeprecationLevel.WARNING,
    )
    override fun onUpdated(callback: (channel: Channel) -> Unit): AutoCloseable {
        return super.onUpdated(callback)
    }

    override fun onThreadChannelUpdated(callback: (ThreadChannel) -> Unit): AutoCloseable {
        return onUpdated { channel ->
            callback(
                ThreadChannelImpl(
                    parentMessage = parentMessage,
                    chat = chat,
                    clock = clock,
                    id = id,
                    name = channel.name,
                    custom = channel.custom,
                    description = channel.description,
                    updated = channel.updated,
                    status = channel.status,
                    type = channel.type,
                    threadCreated = threadCreated
                )
            )
        }
    }

    companion object {
        private val log = Logger.withTag("ThreadChannelImpl")

        internal fun fromDTO(chat: ChatInternal, parentMessage: Message, channel: PNChannelMetadata): ThreadChannel {
            return ThreadChannelImpl(
                parentMessage,
                chat,
                id = channel.id,
                name = channel.name?.value,
                custom = channel.custom?.value,
                description = channel.description?.value,
                updated = channel.updated?.value,
                status = channel.status?.value,
                type = ChannelType.from(channel.type?.value)
            )
        }
    }
}
