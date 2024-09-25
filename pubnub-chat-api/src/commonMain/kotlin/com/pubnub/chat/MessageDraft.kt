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

    fun insertText(offset: Int, text: String): PNFuture<Map<Int, List<SuggestedMention>>>

    fun removeText(offset: Int, length: Int): PNFuture<Map<Int, List<SuggestedMention>>>

    fun insertSuggestedMention(mention: SuggestedMention, text: String): PNFuture<Map<Int, List<SuggestedMention>>>

    fun addMention(offset: Int, length: Int, target: MentionTarget)

    fun removeMention(offset: Int)

    fun update(text: String): PNFuture<Map<Int, List<SuggestedMention>>>

    fun send(
        meta: Map<String, Any>? = null,
        shouldStore: Boolean = true,
        usePost: Boolean = false,
        ttl: Int? = null,
    ): PNFuture<PNPublishResult>

    fun getMessageElements(): List<MessageElement>

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
