package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.error.PubNubErrorMessage
import com.pubnub.kmp.error.PubNubErrorMessage.TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val MINIMAL_TYPING_INDICATOR_TIMEOUT: Duration = 1.seconds

data class Channel(
    private val chat: Chat,
    private val clock: Clock = Clock.System,
    val id: String,
    val name: String? = null,
    val custom: CustomObject? = null,
    val description: String? = null,
    val updated: String? = null,
    val status: String? = null,
    val type: ChannelType? = null,
) {
    private val suggestedNames = mutableMapOf<String, List<Membership>>()
    private var disconnect: (() -> Unit)? = null
    private var typingSent: Instant? = null
    internal var typingIndicators = mutableMapOf<String, Instant>()
    private val sendTextRateLimiter: String? = null // todo should be ExponentialRateLimiter instead of String
    private val typingIndicatorsLock = reentrantLock()

    fun update(
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        updated: String? = null,
        status: String? = null,
        type: ChannelType? = null,
    ): PNFuture<Channel> {
        return chat.updateChannel(id, name, custom, description, updated, status, type)
    }

    fun delete(soft: Boolean = false): PNFuture<Channel> {
        return chat.deleteChannel(id, soft)
    }

    fun forwardMessage(message: Message): PNFuture<PNPublishResult> {
        return chat.forwardMessage(message, this.id)
    }

    fun startTyping(): PNFuture<Unit> {
        if (type == ChannelType.PUBLIC) {
            return PubNubException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message).asFuture()
        }

        val now = clock.now()
        // todo currently in TypeScript there is "this.chat.config.typingTimeout - 1000". Typing timeout is actually 1sec shorter than this.chat.config.typingTimeout
        //  Writing TypeScript wrapper make sure to mimic this behaviour. In KMP the lowest possible value for this timeout is 1000(millis)
        typingSent?.let { typingSentNotNull: Instant ->
            if (!timeoutElapsed(typingSentNotNull, now)) {
                return Unit.asFuture()
            }
        }

        typingSent = now
        return sendTypingSignal(true)
    }

    fun stopTyping(): PNFuture<Unit> {
        if (type == ChannelType.PUBLIC) {
            return PubNubException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message).asFuture()
        }

        typingSent?.let { typingSentNotNull: Instant ->
            val now = clock.now()
            if (timeoutElapsed(typingSentNotNull, now)) {
                return Unit.asFuture()
            }
        } ?: return Unit.asFuture()

        typingSent = null
        return sendTypingSignal(false)
    }

    fun getTyping(callback: (typingUserIds: Collection<String>) -> Unit): AutoCloseable {
        if (type == ChannelType.PUBLIC) {
            throw PubNubException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message)
        }

        return chat.listenForEvents(this.id) { event: Event<EventContent.Typing> ->
            if (event.channelId != id) {
                return@listenForEvents
            }
            val now = clock.now()
            val userId = event.userId
            val isTyping = event.payload.value

            typingIndicatorsLock.withLock {
                updateUserTypingStatus(userId, isTyping, now)
                removeExpiredTypingIndicators(now)
                typingIndicators.keys.toList()
            }.also { typingIndicatorsList ->
                callback(typingIndicatorsList)
            }
        }
    }

    fun whoIsPresent(): PNFuture<Collection<String>> {
        return chat.whoIsPresent(id)
    }

    fun isPresent(userId: String): PNFuture<Boolean> {
        return chat.isPresent(userId, id)
    }

    fun getHistory(
        startTimetoken: Long? = null,
        endTimetoken: Long? = null,
        count: Int? = 25
    ): PNFuture<List<Message>> {
        return chat.pubNub.fetchMessages(
            listOf(id),
            PNBoundedPage(startTimetoken, endTimetoken, count),
            includeMessageActions = true,
            includeMeta = true
        ).then { pnFetchMessagesResult: PNFetchMessagesResult ->
            pnFetchMessagesResult.channels[id]?.map { messageItem: PNFetchMessageItem ->
                val eventContent = try {
                    messageItem.message.asString()?.let { text ->
                        EventContent.TextMessageContent(text, null)
                    } ?: PNDataEncoder.decode(messageItem.message)
                } catch (e: Exception) {
                    EventContent.UnknownMessageFormat(messageItem.message)
                }

                Message(
                    chat,
                    messageItem.timetoken!!,
                    eventContent,
                    id,
                    messageItem.uuid!!,
                    messageItem.actions,
                    messageItem.meta?.decode()?.let { it as Map<String, Any>? }
                )
            } ?: error("Unable to read messages")
        }.catch {
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_HISTORY_DATA.message, it))
        }
    }


    fun sendText(
        text: String,
        meta: Map<String, Any>? = null,
        shouldStore: Boolean? = null,
        usePost: Boolean = false,
        ttl: Int? = null,
        mentionedUsers: Map<Int, MessageMentionedUser>? = null,
        referencedChannels: Map<Int, MessageReferencedChannel>? = null,
        textLinks: List<TextLink>? = null,
        quotedMessage: Message? = null,
        files: List<File>? = null
    ): PNFuture<PNPublishResult> {
        if (quotedMessage != null && quotedMessage.channelId != id) {
            return PubNubException("You cannot quote messages from other channels").asFuture()
        }
        files?.forEach {
            //chat.pubNub todo sendFile here once implemented
        }
        val newMeta = buildMap {
            meta?.let { putAll(it) }
            mentionedUsers?.let { put("mentionedUsers", PNDataEncoder.encode(it)!!) }
            referencedChannels?.let { put("referencedChannels", PNDataEncoder.encode(it)!!) }
            textLinks?.let { put("textLinks", PNDataEncoder.encode(it)!!) }
            quotedMessage?.let {
                put(
                    "quotedMessage", mapOf(
                        "timetoken" to it.timetoken,
                        "text" to it.text,
                        "userId" to it.userId
                    )
                )
            }
        }
        return chat.publish(
            channelId = id,
            message = EventContent.TextMessageContent(text, null), //todo files
            meta = newMeta,
            shouldStore = shouldStore,
            usePost = usePost,
            ttl = ttl,
        ).then { publishResult: PNPublishResult ->
            //todo chat SDK seems to ignore results of emitting these events?
            try {
                mentionedUsers?.forEach {
                    emitUserMention(it.value.id, publishResult.timetoken, text).async {}
                }
            } catch (_: Exception) {
                //todo log
            }
            publishResult
        }
    }


    private fun emitUserMention(
        userId: String,
        timetoken: Long,
        text: String, //todo need to add push payload including this once push is implemented here
    ): PNFuture<PNPublishResult> {
        return chat.emitEvent(userId, EventContent.Mention(timetoken, id))
    }

    private fun timeoutElapsed(lastTypingSent: Instant, now: Instant): Boolean {
        return lastTypingSent < now - maxOf(chat.config.typingTimeout, MINIMAL_TYPING_INDICATOR_TIMEOUT)
    }

    private fun sendTypingSignal(value: Boolean): PNFuture<Unit> {
        return chat.emitEvent(
            channel = this.id,
            payload = EventContent.Typing(value)
        ).then { Unit }
    }

    internal fun setTypingSent(value: Instant) {
        typingSent = value
    }

    internal fun updateUserTypingStatus(userId: String, isTyping: Boolean, now: Instant) {
        if (typingIndicators[userId] != null) {
            if (isTyping) {
                typingIndicators[userId] = now
            } else {
                typingIndicators.remove(userId)
            }
        } else {
            if (isTyping) {
                typingIndicators[userId] = now
            }
        }
    }

    internal fun removeExpiredTypingIndicators(now: Instant) {
        val iterator = typingIndicators.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (timeoutElapsed(entry.value, now)) {
                iterator.remove()
            }
        }
    }
}

enum class ChannelType {
    DIRECT, GROUP, PUBLIC, UNKNOWN;
}
