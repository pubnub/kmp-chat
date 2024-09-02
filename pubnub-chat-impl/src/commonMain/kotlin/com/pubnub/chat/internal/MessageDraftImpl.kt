package com.pubnub.chat.internal

import com.pubnub.chat.Channel
import com.pubnub.chat.Mention
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.SuggestedMention
import com.pubnub.chat.User
import com.pubnub.chat.types.File
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch
import kotlin.math.max
import kotlin.math.min

private val userMentionRegex = Regex("""(?U)(?<=^|\p{Space})(@[\p{L}-]+)""")
private val channelReferenceRegex = Regex("""(?U)(?<=^|\p{Space})(#[\p{LD}-]+)""")

/**
 * This class is not thread safe. All methods on an instance of `MessageDraft` should only be called
 * from a single thread at a time.
 */
class MessageDraftImpl(
    override val channel: Channel,
    override val userSuggestionSource: MessageDraft.UserSuggestionSource = MessageDraft.UserSuggestionSource.CHANNEL,
    override val isTypingIndicatorTriggered: Boolean = true,
    override val userLimit: Int = 10,
    override val channelLimit: Int = 10
) : MessageDraft {
    override var quotedMessage: QuotedMessage? = null
    override val files: MutableList<File> = mutableListOf()

    private val chat = channel.chat
    private var messageText: StringBuilder = StringBuilder("")
    private val mentions: MutableSet<Mention> = sortedSetOf()
    private val diffMatchPatch = DiffMatchPatch()

    private fun revalidateMentions(): PNFuture<Map<Int, List<SuggestedMention>>> {
        val allUserMentions = userMentionRegex.findAll(messageText).toList()
        val allUserMentionStarts = allUserMentions.map { it.matchStart }.toSet()

        val allChannelMentions = channelReferenceRegex.findAll(messageText).toList()
        val allChannelMentionStarts = allUserMentions.map { it.matchStart }.toSet()

        // clean up mentions that no longer start with a @ or #, or are empty strings
        mentions.removeIf { mention ->
            if (mention.length == 0 + (mention.startChar?.let { 1 } ?: 0)) {
                true
            } else {
                when (mention) {
                    is Mention.ChannelReference -> mention.start !in allChannelMentionStarts
                    is Mention.UserMention -> mention.start !in allUserMentionStarts
                    is Mention.TextLink -> false
                }
            }
        }

        // find @s that don't have a Mention attached, we want to get suggestions for them
        val userSuggestionsNeededFor = allUserMentions
            .filter { matchResult ->
                matchResult.matchStart !in mentions
                    .filterIsInstance<Mention.UserMention>()
                    .map(Mention.UserMention::start)
            }

        // find #s that don't have a Mention attached, we want to get suggestions for them
        val channelSuggestionsNeededFor = allChannelMentions
            .filter { matchResult ->
                matchResult.matchStart !in mentions.filterIsInstance<Mention.ChannelReference>()
                    .map(Mention.ChannelReference::start)
            }

        val getSuggestedUsersFuture = userSuggestionsNeededFor
            .filter { matchResult -> matchResult.matchString.length > 3 }
            .map { matchResult ->
                getSuggestedUsers(matchResult.matchString.substring(1)).then {
                    matchResult.matchStart to it.map { user ->
                        SuggestedMention.SuggestedUserMention(matchResult.matchStart, matchResult.matchString, user)
                    }
                }
            }.awaitAll().then { listOfPairs ->
                listOfPairs.toMap()
            }
        val getSuggestedChannelsFuture = channelSuggestionsNeededFor
            .filter { matchResult -> matchResult.matchString.length > 3 }
            .map { matchResult ->
                getSuggestedChannels(matchResult.matchString.substring(1)).then { channels ->
                    matchResult.matchStart to channels.map { channel ->
                        SuggestedMention.SuggestedChannelMention(matchResult.matchStart, matchResult.matchString, channel)
                    }
                }
            }.awaitAll().then { listOfPairs ->
                listOfPairs.toMap()
            }
        return awaitAll(getSuggestedUsersFuture, getSuggestedChannelsFuture).then {
            it.first + it.second
        }
    }

    private fun triggerTypingIndicator() {
        if (isTypingIndicatorTriggered) {
            if (messageText.isNotEmpty()) {
                channel.startTyping()
            } else {
                channel.stopTyping()
            }
        }
    }

    private val MatchResult.matchStart get() = groups[1]!!.range.first
    private val MatchResult.matchString get() = groups[1]!!.value

    override fun insertText(offset: Int, text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        insertTextInternal(offset, text)
        triggerTypingIndicator()
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

    override fun removeText(offset: Int, length: Int): PNFuture<Map<Int, List<SuggestedMention>>> {
        removeTextInternal(offset, length)
        triggerTypingIndicator()
        return revalidateMentions()
    }

    private fun removeTextInternal(offset: Int, length: Int) {
        messageText.delete(offset, offset + length)
        mentions.forEach { mention ->
            val removalEnd = offset + length
            if (offset <= mention.endExclusive && removalEnd >= mention.start) {
                val intersectStart = max(offset, mention.start)
                val intersectEnd = min(removalEnd, mention.endExclusive)
                val intersectLen = intersectEnd - intersectStart
                mention.length -= intersectLen
            }
            if (offset < mention.start) {
                val numCharactersBefore = min(length, mention.start - offset)
                mention.start -= numCharactersBefore
            }
        }
    }

    override fun insertSuggestedMention(mention: SuggestedMention, text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        // TODO validate if the suggestion is still valid or messageText changed in the meantime
        removeTextInternal(mention.start + 1, mention.replaceFrom.length - 1)
        insertTextInternal(mention.start + 1, text)
        when (mention) {
            is SuggestedMention.SuggestedChannelMention -> {
                addMention(Mention.ChannelReference(mention.start, text.length + 1, mention.channel.id))
            }
            is SuggestedMention.SuggestedUserMention -> {
                addMention(Mention.UserMention(mention.start, text.length + 1, mention.user.id))
            }
        }
        triggerTypingIndicator()
        return revalidateMentions()
    }

    override fun addMention(mention: Mention) {
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

    override fun removeMention(offset: Int) {
        mentions.removeIf { it.start == offset }
    }

    override fun removeMention(mention: Mention) {
        mentions.remove(mention)
    }

    override fun update(text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        val diff = diffMatchPatch.diffMain(messageText.toString(), text).apply {
            diffMatchPatch.diffCleanupSemantic(this)
        }
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
        triggerTypingIndicator()
        return revalidateMentions()
    }

    internal fun render(): String {
        val builder = StringBuilder("")
        var consumed = 0
        mentions.forEach { mention ->
            builder.append(messageText.substring(0, mention.start))
            when (mention) {
                is Mention.UserMention -> {
                    builder.append("<user ${mention.userId}>")
                }
                is Mention.ChannelReference -> {
                    builder.append("<channel ${mention.channelId}>")
                }

                is Mention.TextLink -> TODO()
            }
            builder.append(messageText.substring(mention.start, mention.endExclusive))
            when (mention) {
                is Mention.UserMention -> {
                    builder.append("</user>")
                }
                is Mention.ChannelReference -> {
                    builder.append("</channel>")
                }

                is Mention.TextLink -> TODO()
            }
            consumed = mention.endExclusive
        }
        builder.append(messageText.substring(consumed))
        return builder.toString()
    }

    private fun getSuggestedUsers(searchText: String): PNFuture<Collection<User>> {
        return if (userSuggestionSource == MessageDraft.UserSuggestionSource.CHANNEL) {
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
}

fun Map<Int, SuggestedMention>.getSuggestionsFor(position: Int): List<SuggestedMention> =
    filter { it.key <= position }
        .values.filter { suggestedMention ->
            position in suggestedMention.start..(suggestedMention.start + suggestedMention.replaceFrom.length)
        }
