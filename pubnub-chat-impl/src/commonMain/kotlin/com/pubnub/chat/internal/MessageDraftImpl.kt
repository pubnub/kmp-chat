package com.pubnub.chat.internal

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.Channel
import com.pubnub.chat.MentionTarget
import com.pubnub.chat.Message
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.MessageDraftStateListener
import com.pubnub.chat.MessageElement
import com.pubnub.chat.SuggestedMention
import com.pubnub.chat.User
import com.pubnub.chat.types.InputFile
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import name.fraser.neil.plaintext.DiffMatchPatch
import kotlin.math.min

private val userMentionRegex = Regex("""(?U)(?<=^|\p{Space})(@[\p{L}-]+)""")
private val channelReferenceRegex = Regex("""(?U)(?<=^|\p{Space})(#[\p{LD}-]+)""")

private const val SCHEMA_USER = "pn-user://"
private const val SCHEMA_CHANNEL = "pn-channel://"

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

    private val listeners = atomic(setOf<MessageDraftStateListener>())

    override fun addMessageElementsListener(callback: MessageDraftStateListener) {
        listeners.update {
            buildSet {
                addAll(it)
                add(callback)
            }
        }
    }

    override fun removeMessageElementsListener(callback: MessageDraftStateListener) {
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
        val suggestions = getSuggestedMentions()
        listeners.forEach { callback ->
            callback.onChange(messageElements, suggestions)
        }
    }

    private val chat = channel.chat as ChatInternal
    private var messageText: StringBuilder = StringBuilder("")
    private val mentions: MutableList<Mention> = mutableListOf()
    private val diffMatchPatch = DiffMatchPatch()

    private fun getSuggestedMentions(): PNFuture<List<SuggestedMention>> {
        val allUserMentions = userMentionRegex.findAll(messageText).toList()
        val allChannelMentions = channelReferenceRegex.findAll(messageText).toList()

        val userSuggestionsNeededFor = allUserMentions
            .filter { matchResult: MatchResult ->
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
                    it.map { user ->
                        SuggestedMention(matchResult.matchStart, matchResult.matchString, user.name ?: user.id, MentionTarget.User(user.id))
                    }
                }
            }.awaitAll()
        val getSuggestedChannelsFuture = channelSuggestionsNeededFor
            .filter { matchResult -> matchResult.matchString.length > 3 }
            .map { matchResult ->
                getSuggestedChannels(matchResult.matchString.substring(1)).then { channels ->
                    channels.map { channel ->
                        SuggestedMention(
                            matchResult.matchStart,
                            matchResult.matchString,
                            channel.name ?: channel.id,
                            MentionTarget.Channel(channel.id)
                        )
                    }
                }
            }.awaitAll()
        return awaitAll(getSuggestedUsersFuture, getSuggestedChannelsFuture).then {
            it.first.flatten() + it.second.flatten()
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

//    private val MatchResult.matchStart get() = groups[1]!!.range.first // not supported in JS
    private val MatchResult.matchStart get() = range.first
    private val MatchResult.matchString get() = groups[1]!!.value

    override fun insertText(offset: Int, text: String) {
        insertTextInternal(offset, text)
        triggerTypingIndicator()
        fireMessageElementsChanged()
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

    override fun removeText(offset: Int, length: Int) {
        removeTextInternal(offset, length)
        triggerTypingIndicator()
        fireMessageElementsChanged()
    }

    private fun removeTextInternal(offset: Int, length: Int) {
        messageText.deleteRange(offset, offset + length)
        val toRemove = mutableSetOf<Mention>()
        mentions.forEach { mention ->
            val removalEnd = offset + length
            if (offset <= mention.endExclusive && removalEnd > mention.start) {
                toRemove += mention
            }
            if (offset < mention.start) {
                val numCharactersBefore = min(length, mention.start - offset)
                mention.start -= numCharactersBefore
            }
        }
        mentions.removeAll(toRemove)
    }

    override fun insertSuggestedMention(mention: SuggestedMention, text: String) {
        if (messageText.substring(mention.start, mention.start + mention.replaceFrom.length) != mention.replaceFrom) {
            throw PubNubException("This mention suggestion is no longer valid - the message draft text has been changed.")
        }
        removeTextInternal(mention.start, mention.replaceFrom.length)
        insertTextInternal(mention.start, text)
        addMentionInternal(mention.start, text.length, mention.target)
        triggerTypingIndicator()
        fireMessageElementsChanged()
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
        fireMessageElementsChanged()
    }

    override fun removeMention(offset: Int) {
        mentions.removeAll { it.start == offset }
        fireMessageElementsChanged()
    }

    override fun update(text: String) {
        val diff = diffMatchPatch.diff_main(messageText.toString(), text).apply {
            diffMatchPatch.diff_cleanupSemantic(this)
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
        fireMessageElementsChanged()
    }

    override fun send(
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?
    ): PNFuture<PNPublishResult> = channel.sendText(
        text = render(getMessageElements()),
        meta = meta,
        shouldStore = shouldStore,
        usePost = usePost,
        ttl = ttl,
        quotedMessage = quotedMessage,
        files = files,
        usersToMention = mentions.mapNotNull { it.target as? MentionTarget.User }.map { it.userId }
    )

    internal fun getMessageElements(): List<MessageElement> =
        getMessageElements(messageText, mentions)

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

    companion object {
        private val linkRegex = Regex("""\[(?<text>(?:[^]]*?(?:\\\\)*(?:\\])*)+?)]\((?<link>(?:[^)]*?(?:\\\\)*(?:\\\))*)+?)\)""")

        internal fun escapeLinkText(text: String) = text.replace("\\", "\\\\").replace("]", "\\]")

        internal fun unEscapeLinkText(text: String) = text.replace("\\]", "]").replace("\\\\", "\\")

        internal fun escapeLinkUrl(text: String) = text.replace("\\", "\\\\").replace(")", "\\)")

        internal fun unEscapeLinkUrl(text: String) = text.replace("\\)", ")").replace("\\\\", "\\")

        /**
         * Parses text from a received message that includes Markdown links into a list of [MessageElement]
         */
        fun getMessageElements(markdownText: String): List<MessageElement> {
            return buildList {
                var offset = 0
                while (true) {
                    val found = linkRegex.find(markdownText, offset) ?: break
                    val linkText = unEscapeLinkText(found.groups["text"]!!.value)
                    val linkTarget = unEscapeLinkUrl(found.groups["link"]!!.value)
                    markdownText.substring(offset, found.range.first).takeIf { it.isNotEmpty() }?.let {
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
                markdownText.substring(offset).takeIf { it.isNotEmpty() }?.let {
                    add(MessageElement.PlainText(it))
                }
            }
        }

        /**
         * Combines a list of mentions and draft plain text into a list of [MessageElement]
         */
        internal fun getMessageElements(plainText: CharSequence, mentions: List<Mention>): List<MessageElement> {
            return buildList {
                var consumedUntil = 0
                mentions.sorted().forEach { mention ->
                    plainText.substring(consumedUntil, mention.start).takeIf { it.isNotEmpty() }?.let {
                        add(MessageElement.PlainText(it))
                    }
                    add(MessageElement.Link(plainText.substring(mention.start, mention.endExclusive), mention.target))
                    consumedUntil = mention.endExclusive
                }
                plainText.substring(consumedUntil).takeIf { it.isNotEmpty() }?.let {
                    add(MessageElement.PlainText(it))
                }
            }
        }

        internal fun render(messageElements: List<MessageElement>): String {
            val builder = StringBuilder(messageElements.sumOf { it.text.length })
            messageElements.forEach { element ->
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
    }
}

fun List<SuggestedMention>.getSuggestionsFor(position: Int): List<SuggestedMention> =
    filter { it.start <= position }
        .filter { suggestedMention ->
            position in suggestedMention.start..(suggestedMention.start + suggestedMention.replaceFrom.length)
        }

class Mention(
    var start: Int,
    var length: Int,
    val target: MentionTarget,
) : Comparable<Mention> {
    val endExclusive get() = start + length

    override fun compareTo(other: Mention): Int {
        return start.compareTo(other.start)
    }
}
