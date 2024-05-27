package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.kmp.error.PubNubErrorMessage.TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.Timer
import com.pubnub.kmp.types.TimerImpl

private const val EVENT_TYPE_TYPING = "typing"

internal const val ONE_SECOND = 1000L

data class Channel(
    private val chat: Chat,
    private val time: Timer = TimerImpl(),
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
    private var typingSent: Long? = null
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

        val currentTimeStampInMillis = time.getCurrentTimeStampInMillis()
        if (typingSent != null && typingSent!! > currentTimeStampInMillis - chat.config.typingTimeout - ONE_SECOND) {
            callback(Result.success(Unit))
            return
        }

        typingSent = currentTimeStampInMillis
        sendTypingSignal(true, callback)
    }

    fun stopTyping(callback: (Result<Unit>) -> Unit) {
        if (type == ChannelType.PUBLIC) {
            callback(Result.failure(Exception(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message)))
            return
        }
        if (typingSent == null) {
            callback(Result.success(Unit))
            return
        }
        typingSent = null
        sendTypingSignal(false, callback)
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

    internal fun setTypingSent(value: Long) {
        typingSent = value
    }
}

enum class ChannelType {
    DIRECT, GROUP, PUBLIC;
}
