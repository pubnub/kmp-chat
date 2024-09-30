package com.pubnub.chat

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.types.InputFile
import com.pubnub.kmp.PNFuture

interface MessageDraft {
    val channel: Channel
    val userSuggestionSource: UserSuggestionSource
    val isTypingIndicatorTriggered: Boolean
    val userLimit: Int
    val channelLimit: Int
    var quotedMessage: Message?
    val files: MutableList<InputFile>

    fun addMessageElementsListener(callback: MessageDraftStateListener)

    fun removeMessageElementsListener(callback: MessageDraftStateListener)

    fun insertText(offset: Int, text: String)

    fun removeText(offset: Int, length: Int)

    fun insertSuggestedMention(mention: SuggestedMention, text: String)

    fun addMention(offset: Int, length: Int, target: MentionTarget)

    fun removeMention(offset: Int)

    fun update(text: String)

    fun send(
        meta: Map<String, Any>? = null,
        shouldStore: Boolean = true,
        usePost: Boolean = false,
        ttl: Int? = null,
    ): PNFuture<PNPublishResult>

    enum class UserSuggestionSource {
        GLOBAL,
        CHANNEL
    }
}

sealed interface MentionTarget {
    data class User(val userId: String) : MentionTarget

    data class Channel(val channelId: String) : MentionTarget

    data class Url(val url: String) : MentionTarget
}

sealed interface MessageElement {
    val text: String

    data class PlainText(override val text: String) : MessageElement

    data class Link(override val text: String, val target: MentionTarget) : MessageElement
}

class SuggestedMention(val start: Int, val replaceFrom: String, val replaceTo: String, val target: MentionTarget)

fun interface MessageDraftStateListener {
    fun onChange(messageElements: List<MessageElement>, suggestedMentions: PNFuture<List<SuggestedMention>>)
}
