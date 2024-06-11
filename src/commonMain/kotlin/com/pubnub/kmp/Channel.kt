package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.callbacks.map
import com.pubnub.api.v2.callbacks.mapCatching
import com.pubnub.api.v2.callbacks.wrapException
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.error.PubNubErrorMessage
import com.pubnub.kmp.error.PubNubErrorMessage.TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
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
    private var typingIndicators =
        mutableMapOf<String, String>() //todo probably should be something like mutableMapOf<String, TimerTask>()
    private val sendTextRateLimiter: String? = null // todo should be ExponentialRateLimiter instead of String

    fun update(
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        updated: String? = null,
        status: String? = null,
        type: ChannelType? = null,
        callback: (Result<Channel>) -> Unit
    ) {
        chat.updateChannel(id, name, custom, description, updated, status, type, callback)
    }

    fun delete(soft: Boolean = false, callback: (Result<Channel>) -> Unit) {
        chat.deleteChannel(id, soft, callback)
    }

    fun forwardMessage(message: Message, callback: (Result<Unit>) -> Unit) {
        chat.forwardMessage(message, this.id, callback)
    }

    fun startTyping(callback: (Result<Unit>) -> Unit) {
        if (type == ChannelType.PUBLIC) {
            callback(Result.failure(Exception(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message)))
            return
        }

        val now = clock.now()
        // todo currently in TypeScript there is "this.chat.config.typingTimeout - 1000". Typing timeout is actually 1sec shorter than this.chat.config.typingTimeout
        //  Writing TypeScript wrapper make sure to mimic this behaviour. In KMP the lowest possible value for this timeout is 1000(millis)
        typingSent?.let { typingSentNotNull: Instant ->
            if (!timeoutElapsed(typingSentNotNull, now)) {
                callback(Result.success(Unit))
                return
            }
        }

        typingSent = now
        sendTypingSignal(true, callback)
    }

    fun stopTyping(callback: (Result<Unit>) -> Unit) {
        if (type == ChannelType.PUBLIC) {
            callback(Result.failure(Exception(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message)))
            return
        }

        typingSent?.let { typingSentNotNull: Instant ->
            val now = clock.now()
            if(timeoutElapsed(typingSentNotNull, now)) {
                callback(Result.success(Unit))
                return
            }
        } ?: run {
            callback(Result.success(Unit))
            return
        }
        typingSent = null
        sendTypingSignal(false, callback)
    }

    fun whoIsPresent(callback: (Result<Collection<String>>) -> Unit) {
        chat.whoIsPresent(id, callback)
    }

    fun isPresent(userId: String, callback: (Result<Boolean>) -> Unit) {
        chat.isPresent(userId, id, callback)
    }

    fun getHistory(startTimetoken: Long? = null, endTimetoken: Long? = null, count: Int? = 25, callback: (Result<List<Message>>) -> Unit) {
        chat.pubNub.fetchMessages(
            listOf(id),
            PNBoundedPage(startTimetoken, endTimetoken, count),
            includeMessageActions = true,
            includeMeta = true
        ).async { result: Result<PNFetchMessagesResult> ->
            callback(result.mapCatching { value ->
                value.channels[id]?.map { messageItem: PNFetchMessageItem ->
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
                        messageItem.meta?.decode()?.let { it as Map<String,Any>? }
                    )
                } ?: error("Unable to read messages")
            }.wrapException {
                PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_HISTORY_DATA.message, it)
            })
        }
    }

    fun sendText(
        text: String,
        meta: Map<String,Any>? = null,
        shouldStore: Boolean? = null,
        usePost: Boolean = false,
        ttl: Int? = null,
        mentionedUsers: Map<Int, MessageMentionedUser>? = null,
        referencedChannels: Map<Int, MessageReferencedChannel>? = null,
        textLinks: List<TextLink>? = null,
        quotedMessage: Message? = null,
        files: List<File>? = null,
        callback: (Result<PNPublishResult>) -> Unit
    ) {
        if (quotedMessage != null && quotedMessage.channelId != id) {
            callback(Result.failure(IllegalArgumentException("You cannot quote messages from other channels")))
            return
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
                put("quotedMessage", mapOf(
                    "timetoken" to it.timetoken,
                    "text" to it.text,
                    "userId" to it.userId
                ))
            }
        }
        chat.publish(
            channelId = id,
            message = EventContent.TextMessageContent(text, null), //todo files
            meta = newMeta,
            shouldStore = shouldStore,
            usePost = usePost,
            ttl = ttl,
        ) { result: Result<PNPublishResult> ->
            result.onSuccess { publishResult: PNPublishResult ->
                //todo chat SDK seems to ignore results of emitting these events?
                try {
                    mentionedUsers?.forEach {
                        emitUserMention(it.value.id, publishResult.timetoken, text) { }
                    }
                } catch (_: Exception) {}
            }
            callback(result)
        }
    }

    private fun emitUserMention(
        userId: String,
        timetoken: Long,
        text: String, //todo need to add push payload including this once push is implemented here
        callback: (Result<PNPublishResult>) -> Unit
    ) {
        chat.emitEvent(userId, EventContent.Mention(timetoken, id), callback)
    }

    private fun timeoutElapsed(lastTypingSent: Instant, now: Instant): Boolean {
        return lastTypingSent < now - maxOf(chat.config.typingTimeout, MINIMAL_TYPING_INDICATOR_TIMEOUT)
    }

    private fun sendTypingSignal(value: Boolean, callback: (Result<Unit>) -> Unit) {
        chat.emitEvent(
            channel = this.id,
            payload = EventContent.Typing(value),
            callback = { result: Result<PNPublishResult> ->
                callback(result.map { Unit })
            }
        )
    }

    internal fun setTypingSent(value: Instant) {
        typingSent = value
    }
}

enum class ChannelType {
    DIRECT, GROUP, PUBLIC;
}
