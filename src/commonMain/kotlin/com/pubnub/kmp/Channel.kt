package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.error.PubNubErrorMessage.TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val EVENT_TYPE_TYPING = "typing"
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

    private fun timeoutElapsed(lastTypingSent: Instant, now: Instant): Boolean {
        return lastTypingSent < now - maxOf(chat.config.typingTimeout, MINIMAL_TYPING_INDICATOR_TIMEOUT)
    }

    private fun sendTypingSignal(value: Boolean, callback: (Result<Unit>) -> Unit) {
        chat.emitEvent(
            channel = this.id,
            method = EmitEventMethod.SIGNAL,
            type = EVENT_TYPE_TYPING,
            payload = EventContent.Typing(value),
            callback = { result: Result<PNPublishResult> ->
                result.onSuccess {
                    callback(Result.success(Unit))
                }.onFailure { exception: PubNubException ->
                    callback(Result.failure(exception))
                }
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
