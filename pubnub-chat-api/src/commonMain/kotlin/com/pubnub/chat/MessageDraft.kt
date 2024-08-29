package com.pubnub.chat

import com.pubnub.chat.types.File
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch
import kotlin.math.min

private val userMentionRegex = Regex("""(?U)(?:^|\p{Space})(@[\p{L}-]+)""")
private val channelReferenceRegex = Regex("""(?U)(?:^|\p{Space})(#[\p{LD}-]+)""")

/**
 * This class is not thread safe. All methods on an instance of `MessageDraft` should only be called
 * from a single thread at a time.
 */
class MessageDraft(
    val channel: Channel,
    val userSuggestionSource: UserSuggestionSource = UserSuggestionSource.CHANNEL,
    val isTypingIndicatorTriggered: Boolean = true,
    val userLimit: Int = 10,
    val channelLimit: Int = 10
) {
    var quotedMessage: QuotedMessage? = null
    val files: MutableList<File> = mutableListOf()

    private val chat = channel.chat
    private var messageText: StringBuilder = StringBuilder("")
    private val mentions: MutableSet<Mention> = sortedSetOf()

    private fun revalidateMentions(): PNFuture<Map<Int, List<SuggestedMention>>> {
        val allUserMentions = userMentionRegex.findAll(messageText).toList()
        val allUserMentionStarts = allUserMentions.map { it.markerStart() }

        val allChannelMentions = channelReferenceRegex.findAll(messageText).toList()
        val allChannelMentionStarts = allUserMentions.map { it.markerStart() }

        mentions.removeIf {
            (it is Mention.UserMention && it.start !in allUserMentionStarts) ||
                (it is Mention.ChannelReference && it.start !in allChannelMentionStarts)
        }
        if (isTypingIndicatorTriggered) {
            if (messageText.isNotEmpty()) {
                channel.startTyping()
            } else {
                channel.stopTyping()
            }
        }
        val userSuggestionsNeededFor = allUserMentions
            .filter { matchResult ->
                matchResult.markerStart() !in mentions.filterIsInstance<Mention.UserMention>()
                    .map(Mention.UserMention::start)
            }

        val channelSuggestionsNeededFor = allChannelMentions
            .filter { matchResult ->
                matchResult.markerStart() !in mentions.filterIsInstance<Mention.ChannelReference>()
                    .map(Mention.ChannelReference::start)
            }

        val getUsers = userSuggestionsNeededFor
            .filter { matchResult -> matchResult.matchString().length >= 3 }
            .map { matchResult ->
                getSuggestedUsers(matchResult.matchString().substring(1)).then {
                    matchResult.markerStart() to it.map { user ->
                        SuggestedMention.SuggestedUserMention(matchResult.markerStart(), matchResult.matchString(), user)
                    }
                }
            }.awaitAll().then { listOfPairs ->
                listOfPairs.associate { it }
            }
        val getChannels = channelSuggestionsNeededFor
            .filter { matchResult -> matchResult.matchString().length >= 3 }
            .map { matchResult ->
                getSuggestedChannels(matchResult.matchString().substring(1)).then {
                    matchResult.markerStart() to it.map { channel ->
                        SuggestedMention.SuggestedChannelMention(matchResult.markerStart(), matchResult.matchString(), channel)
                    }
                }
            }.awaitAll().then { listOfPairs ->
                listOfPairs.associate { it }
            }
        return awaitAll(getUsers, getChannels).then {
            buildMap {
                putAll(it.first)
                putAll(it.second)
            }
        }
    }

    private fun MatchResult.markerStart() = groups[1]!!.range.first

    private fun MatchResult.matchString() = groups[1]!!.value

    fun insertText(offset: Int, text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        insertTextInternal(offset, text)
        return revalidateMentions()
    }

    private fun insertTextInternal(offset: Int, text: String) {
        messageText.insert(offset, text)
        mentions.forEach { mention ->
            when {
                offset > mention.start && offset < mention.endExclusive -> {
                    mention.length += text.length
                }
                offset <= mention.start -> {
                    mention.start += text.length
                }
            }
        }
    }

    fun removeText(offset: Int, length: Int): PNFuture<Map<Int, List<SuggestedMention>>> {
        removeTextInternal(offset, length)
        return revalidateMentions()
    }

    private fun removeTextInternal(offset: Int, length: Int) {
        messageText.delete(offset, offset + length)
        val mentionsToDelete = mutableSetOf<Mention>()
        mentions.forEach { mention ->
            when {
                offset > mention.start && offset < mention.endExclusive -> {
                    mention.length -= min(length, mention.start + mention.length - offset)
                }
                offset + length < mention.start -> {
                    mention.start -= length
                }
                offset <= mention.start && offset + length > mention.start -> {
                    mentionsToDelete += mention
                }
                offset <= mention.start -> {
                    mention.start -= length
                }
            }
        }
        mentions.removeAll(mentionsToDelete)
    }

    fun insertMention(mention: SuggestedMention, text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        // TODO validate if the suggestion is still valid or messageText changed in the meantime
        removeTextInternal(mention.start + 1, mention.replaceFrom.length - 1)
        insertTextInternal(mention.start + 1, text)
        when (mention) {
            is SuggestedMention.SuggestedChannelMention -> {
                insertMention(Mention.ChannelReference(mention.start, text.length + 1, mention.channel))
            }
            is SuggestedMention.SuggestedUserMention -> {
                insertMention(Mention.UserMention(mention.start, text.length + 1, mention.user))
            }
        }
        return revalidateMentions()
    }

    fun insertMention(mention: Mention) {
        if (mention.startChar != null) {
            require(
                messageText[mention.start] == mention.startChar
            ) { "Starting character ${mention.startChar} not found at ${mention.start}" }
        }
        mentions.forEach {
            require((mention.start >= it.endExclusive) && (mention.endExclusive <= it.start)) { "Cannot intersect with existing mention: " }
        }
        mentions.add(mention)
    }

    fun removeMention(offset: Int) {
        mentions.removeIf { it.start == offset }
    }

    fun removeMention(mention: Mention) {
        mentions.remove(mention)
    }

    fun update(text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        val diff = DiffMatchPatch().diffMain(messageText.toString(), text)
        var consumed = 0
        diff.forEach { action ->
            when (action.operation) {
                DiffMatchPatch.Operation.DELETE -> removeTextInternal(consumed, action.text.length)
                DiffMatchPatch.Operation.INSERT -> {
                    insertTextInternal(consumed, action.text)
                    consumed += action.text.length
                }
                DiffMatchPatch.Operation.EQUAL -> {
                    consumed += action.text.length
                }
            }
        }
        return revalidateMentions()
    }

    fun render() {
        println("========")
        var consumed = 0
        mentions.forEach { mention ->
            print(messageText.substring(0, mention.start))
            when (mention) {
                is Mention.UserMention -> {
                    print("<user ${mention.user.id}>")
                }
                is Mention.ChannelReference -> {
                    print("<channel ${mention.channel.id}>")
                }

                is Mention.TextLink -> TODO()
            }
            print(messageText.substring(mention.start, mention.endExclusive))
            when (mention) {
                is Mention.UserMention -> {
                    print("</user>")
                }
                is Mention.ChannelReference -> {
                    print("</channel>")
                }

                is Mention.TextLink -> TODO()
            }
            consumed = mention.endExclusive
        }
        println(messageText.substring(consumed))
    }

    private fun getSuggestedUsers(searchText: String): PNFuture<Collection<User>> {
        return if (userSuggestionSource == UserSuggestionSource.CHANNEL) {
            channel.getUserSuggestions(searchText, userLimit).then { memberships ->
                memberships.map { it.user }
            }
        } else {
            chat.getUserSuggestions(searchText, userLimit)
        }
    }

    private fun getSuggestedChannels(searchText: String): PNFuture<Collection<Channel>> {
        return chat.getChannelSuggestions(searchText, channelLimit)
    }

    enum class UserSuggestionSource {
        GLOBAL,
        CHANNEL
    }
}


sealed class Mention : Comparable<Mention> {
    abstract var start: Int
    abstract var length: Int
    val endExclusive get() = start + length
    abstract val startChar: Char?

    class UserMention(override var start: Int, override var length: Int, var user: User) : Mention() {
        override val startChar: Char = '@'
    }

    class ChannelReference(override var start: Int, override var length: Int, var channel: Channel) : Mention() {
        override val startChar: Char = '#'
    }

    class TextLink(override var start: Int, override var length: Int, var url: String) : Mention() {
        override val startChar: Char? = null
    }

    override fun compareTo(other: Mention): Int {
        return start.compareTo(other.start)
    }
}

sealed class SuggestedMention(val start: Int, val replaceFrom: String) {
    class SuggestedUserMention(start: Int, replaceFrom: String, val user: User): SuggestedMention(start, replaceFrom)
    class SuggestedChannelMention(start: Int, replaceFrom: String, val channel: Channel): SuggestedMention(start, replaceFrom)
}