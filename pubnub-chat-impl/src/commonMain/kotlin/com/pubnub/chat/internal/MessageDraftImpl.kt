package com.pubnub.chat.internal

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.Channel
import com.pubnub.chat.MentionTarget
import com.pubnub.chat.Message
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.MessageElement
import com.pubnub.chat.SuggestedMention
import com.pubnub.chat.User
import com.pubnub.chat.types.InputFile
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import name.fraser.neil.plaintext.diff_match_patch
import kotlin.math.min

private val userMentionRegex = Regex("""(?U)(?<=^|\p{Space})(@[\p{L}-]+)""")
private val channelReferenceRegex = Regex("""(?U)(?<=^|\p{Space})(#[\p{LD}-]+)""")

private const val SCHEMA_USER = "pn-user://"
private const val SCHEMA_CHANNEL = "pn-channel://"

typealias MessageElementsListener = (List<MessageElement>) -> Unit

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
    override var quotedMessage: Message? = null
    override val files: MutableList<InputFile> = mutableListOf()

    private val listeners = atomic(setOf<MessageElementsListener>())

    override fun addMessageElementsListener(callback: MessageElementsListener) {
        listeners.update {
            buildSet {
                addAll(it)
                add(callback)
            }
        }
    }

    override fun removeMessageElementsListener(callback: MessageElementsListener) {
        listeners.update {
            it.filterNot { element -> element == callback }.toSet()
        }
    }

    private fun fireMessageElementsChanged() {
        val listeners = listeners.value
        if (listeners.isEmpty()) {
            return
        }
        val messageElements = getMessageElements()
        listeners.forEach { callback ->
            callback(messageElements)
        }
    }

    private val chat = channel.chat as ChatInternal
    private var messageText: StringBuilder = StringBuilder("")
    private val mentions: MutableSet<Mention> = mutableSetOf()
    private val diffMatchPatch = diff_match_patch()

    private fun revalidateMentions(): PNFuture<Map<Int, List<SuggestedMention>>> {
        val allUserMentions = userMentionRegex.findAll(messageText).toList()
        val allChannelMentions = channelReferenceRegex.findAll(messageText).toList()

        val userSuggestionsNeededFor = allUserMentions
            .filter { matchResult: MatchResult ->
                println(matchResult.groupValues)
                matchResult.matchStart !in mentions
                    .filter { it.target is MentionTarget.User }
                    .map(Mention::start)
            }
        val channelSuggestionsNeededFor = allChannelMentions
            .filter { matchResult ->
                matchResult.matchStart !in mentions
                    .filter { it.target is MentionTarget.Channel }
                    .map(Mention::start)
            }

        val getSuggestedUsersFuture = userSuggestionsNeededFor
            .filter { matchResult -> matchResult.matchString.length > 3 }
            .map { matchResult: MatchResult ->
                getSuggestedUsers(matchResult.matchString.substring(1)).then {
                    matchResult.matchStart to it.map { user ->
                        SuggestedMention(matchResult.matchStart, matchResult.matchString, user.name ?: user.id, MentionTarget.User(user.id))
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
                        SuggestedMention(matchResult.matchStart, matchResult.matchString, channel.name ?: channel.id, MentionTarget.Channel(channel.id))
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

//    private val MatchResult.matchStart get() = groups[1]!!.range.first
    private val MatchResult.matchStart get() = range.first
    private val MatchResult.matchString get() = groups[1]!!.value

    override fun insertText(offset: Int, text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        insertTextInternal(offset, text)
        triggerTypingIndicator()
        return revalidateMentions().also {
            fireMessageElementsChanged()
        }
    }

    private fun insertTextInternal(offset: Int, text: String) {
        messageText.insert(offset, text)
        val toRemove = mutableSetOf<Mention>()
        mentions.forEach { mention ->
            when {
                offset > mention.start && offset < mention.endExclusive -> {
                    toRemove += mention
                }
                offset <= mention.start -> {
                    mention.start += text.length
                }
            }
        }
        mentions.removeAll(toRemove)
    }

    override fun removeText(offset: Int, length: Int): PNFuture<Map<Int, List<SuggestedMention>>> {
        removeTextInternal(offset, length)
        triggerTypingIndicator()
        return revalidateMentions().also {
            fireMessageElementsChanged()
        }
    }

    private fun removeTextInternal(offset: Int, length: Int) {
        messageText.deleteRange(offset, offset + length)
        val toRemove = mutableSetOf<Mention>()
        mentions.forEach { mention ->
            val removalEnd = offset + length
            if (offset <= mention.endExclusive && removalEnd >= mention.start) {
                toRemove += mention
            }
            if (offset < mention.start) {
                val numCharactersBefore = min(length, mention.start - offset)
                mention.start -= numCharactersBefore
            }
        }
        mentions.removeAll(toRemove)
    }

    override fun insertSuggestedMention(mention: SuggestedMention, text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        if (messageText.substring(mention.start, mention.start + mention.replaceFrom.length) != mention.replaceFrom) {
            throw PubNubException("This mention suggestion is no longer valid - the message draft text has been changed.")
        }
        removeTextInternal(mention.start, mention.replaceFrom.length)
        insertTextInternal(mention.start, text)
        addMentionInternal(mention.start, text.length, mention.target)
        triggerTypingIndicator()
        return revalidateMentions().also {
            fireMessageElementsChanged()
        }
    }

    private fun addMentionInternal(offset: Int, length: Int, target: MentionTarget) {
        val mention = Mention(offset, length, target)
        mentions.forEach {
            require(
                (mention.start >= it.endExclusive || mention.endExclusive <= it.start)
            ) { "Cannot intersect with existing mention: ${it.start} ${it.endExclusive}, ${mention.start} ${mention.endExclusive}" }
        }
        mentions.add(mention)
    }

    override fun addMention(offset: Int, length: Int, target: MentionTarget) {
        addMentionInternal(offset, length, target)
        // todo revalidateMentions?
        fireMessageElementsChanged()
    }

    override fun removeMention(offset: Int) {
        mentions.removeAll { it.start == offset }
        fireMessageElementsChanged()
    }

    override fun update(text: String): PNFuture<Map<Int, List<SuggestedMention>>> {
        val diff = diffMatchPatch.diff_main(messageText.toString(), text).apply {
            diffMatchPatch.diff_cleanupSemantic(this)
        }
        var consumed = 0
        diff.forEach { action ->
            when (action.operation) {
                diff_match_patch.Operation.DELETE -> removeTextInternal(consumed, action.text.length)
                diff_match_patch.Operation.INSERT -> {
                    insertTextInternal(consumed, action.text)
                    consumed += action.text.length
                }
                diff_match_patch.Operation.EQUAL -> {
                    consumed += action.text.length
                }
            }
        }
        triggerTypingIndicator()
        return revalidateMentions().also {
            fireMessageElementsChanged()
        }
    }

    override fun send(
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?
    ): PNFuture<PNPublishResult> = channel.sendText(
        text = render(),
        meta = meta,
        shouldStore = shouldStore,
        usePost = usePost,
        ttl = ttl,
        quotedMessage = quotedMessage,
        files = files,
        usersToMention = mentions.mapNotNull { it.target as? MentionTarget.User }.map { it.userId }
    )

    override fun getMessageElements(): List<MessageElement> {
        return buildList {
            var consumedUntil = 0
            mentions.forEach { mention ->
                messageText.substring(consumedUntil, mention.start).takeIf { it.isNotEmpty() }?.let {
                    add(MessageElement.PlainText(it))
                }
                add(MessageElement.Link(messageText.substring(mention.start, mention.endExclusive), mention.target))
                consumedUntil = mention.endExclusive
            }
            messageText.substring(consumedUntil).takeIf { it.isNotEmpty() }?.let {
                add(MessageElement.PlainText(it))
            }
        }
    }

    internal fun render(): String {
        val builder = StringBuilder(messageText.length)
        getMessageElements().forEach { element ->
            when (element) {
                is MessageElement.Link -> {
                    builder.append("[${escapeLinkText(element.text)}]")
                    when (val target = element.target) {
                        is MentionTarget.User -> {
                            builder.append("(${escapeLinkUrl(SCHEMA_USER + target.userId)})")
                        }

                        is MentionTarget.Channel -> {
                            builder.append("(${escapeLinkUrl(SCHEMA_CHANNEL + target.channelId)})")
                        }

                        is MentionTarget.Url -> {
                            builder.append("(${escapeLinkUrl(target.url)})")
                        }
                    }
                }
                is MessageElement.PlainText -> builder.append(element.text)
            }
        }
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

class Mention(
    var start: Int,
    var length: Int,
    val target: MentionTarget,
) : Comparable<Mention> {
    val endExclusive get() = start + length

//    val startChar: Char? = when (target) {
//        is MentionTarget.User -> '@'
//        is MentionTarget.Channel -> '#'
//        is MentionTarget.Url -> null
//    }

    override fun compareTo(other: Mention): Int {
        return start.compareTo(other.start)
    }
}

private val linkRegex = Regex("""\[(?<text>(?:[^]]*?(?:\\\\)*(?:\\])*)+?)]\((?<link>(?:[^)]*?(?:\\\\)*(?:\\\))*)+?)\)""")

fun escapeLinkText(text: String) = text.replace("\\", "\\\\").replace("]", "\\]")

fun unEscapeLinkText(text: String) = text.replace("\\]", "]").replace("\\\\", "\\")

fun escapeLinkUrl(text: String) = text.replace("\\", "\\\\").replace(")", "\\)")

fun unEscapeLinkUrl(text: String) = text.replace("\\)", ")").replace("\\\\", "\\")

fun messageElements(text: String): List<MessageElement> {
    return buildList {
        var offset = 0
        while (true) {
            val found = linkRegex.find(text, offset) ?: break
            val linkText = unEscapeLinkText(found.groups["text"]!!.value)
            val linkTarget = unEscapeLinkUrl(found.groups["link"]!!.value)
            text.substring(offset, found.range.first).takeIf { it.isNotEmpty() }?.let {
                add(MessageElement.PlainText(it))
            }
            when {
                linkTarget.startsWith(SCHEMA_USER) -> {
                    add(
                        MessageElement.Link(
                            linkText,
                            MentionTarget.User(
                                linkTarget.substring(SCHEMA_USER.length)
                            )
                        )
                    )
                }

                linkTarget.startsWith(SCHEMA_CHANNEL) -> {
                    add(
                        MessageElement.Link(
                            linkText,
                            MentionTarget.Channel(
                                linkTarget.substring(SCHEMA_CHANNEL.length)
                            )
                        )
                    )
                }

                else -> {
                    add(MessageElement.Link(linkText, MentionTarget.Url(linkTarget)))
                }
            }
            offset = found.range.last + 1
        }
        text.substring(offset).takeIf { it.isNotEmpty() }?.let {
            add(MessageElement.PlainText(it))
        }
    }
}
