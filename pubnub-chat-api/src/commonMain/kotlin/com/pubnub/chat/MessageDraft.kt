package com.pubnub.chat

import com.pubnub.chat.types.File
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.kmp.PNFuture
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

interface MessageDraft {
    val channel: Channel
    val userSuggestionSource: UserSuggestionSource
    val isTypingIndicatorTriggered: Boolean
    val userLimit: Int
    val channelLimit: Int
    var quotedMessage: QuotedMessage?
    val files: MutableList<File>

    fun insertText(offset: Int, text: String): PNFuture<Map<Int, List<SuggestedMention>>>

    fun removeText(offset: Int, length: Int): PNFuture<Map<Int, List<SuggestedMention>>>

    fun insertSuggestedMention(mention: SuggestedMention, text: String): PNFuture<Map<Int, List<SuggestedMention>>>

    fun addMention(mention: Mention)

    fun removeMention(offset: Int)

    fun removeMention(mention: Mention)

    fun update(text: String): PNFuture<Map<Int, List<SuggestedMention>>>

    enum class UserSuggestionSource {
        GLOBAL,
        CHANNEL
    }
}

@Serializable
sealed class Mention : Comparable<Mention> {
    @SerialName("s")
    abstract var start: Int

    @SerialName("l")
    abstract var length: Int

    val endExclusive get() = start + length

    abstract val startChar: Char?

    @Serializable
    @SerialName("usr")
    class UserMention(
        override var start: Int,
        override var length: Int,
        @SerialName("v") var userId: String
    ) : Mention() {
        @Transient
        override val startChar: Char = '@'
    }

    @Serializable
    @SerialName("cha")
    class ChannelReference(
        override var start: Int,
        override var length: Int,
        @SerialName("v") var channelId: String
    ) : Mention() {
        @Transient
        override val startChar: Char = '#'
    }

    @Serializable
    @SerialName("url")
    class TextLink(
        override var start: Int,
        override var length: Int,
        @SerialName("v") var url: String
    ) : Mention() {
        @Transient
        override val startChar: Char? = null
    }

    override fun compareTo(other: Mention): Int {
        return start.compareTo(other.start)
    }
}

sealed class SuggestedMention(val start: Int, val replaceFrom: String) {
    class SuggestedUserMention(start: Int, replaceFrom: String, val user: User) : SuggestedMention(start, replaceFrom)

    class SuggestedChannelMention(start: Int, replaceFrom: String, val channel: Channel) : SuggestedMention(start, replaceFrom)
}
