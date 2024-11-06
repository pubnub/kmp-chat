package com.pubnub.chat.internal

import com.pubnub.api.PubNubException
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.Channel
import com.pubnub.chat.MentionTarget
import com.pubnub.chat.Message
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.MessageDraftChangeListener
import com.pubnub.chat.MessageElement
import com.pubnub.chat.SuggestedMention
import com.pubnub.chat.User
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.InputFile
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import name.fraser.neil.plaintext.DiffMatchPatch
import kotlin.math.min

private const val SCHEMA_USER = "pn-user://"
private const val SCHEMA_CHANNEL = "pn-channel://"

/**
 * This class is not thread safe. All methods on an instance of `MessageDraft` should only be called
 * from a single thread at a time.
 */
class MessageDraftImpl(
    override val channel: Channel,
    override val userSuggestionSource: MessageDraft.UserSuggestionSource = MessageDraft.UserSuggestionSource.CHANNEL,
    override val isTypingIndicatorTriggered: Boolean = channel.type != ChannelType.PUBLIC,
    override val userLimit: Int = 10,
    override val channelLimit: Int = 10
) : MessageDraft {
    override var quotedMessage: Message? = null
    override val files: MutableList<InputFile> = mutableListOf()

    private val listeners = atomic(setOf<MessageDraftChangeListener>())
    private val chat = channel.chat as ChatInternal
    private val mentions: MutableList<Mention> = mutableListOf()
    private val diffMatchPatch = DiffMatchPatch()

    private var messageText: StringBuilder = StringBuilder("")

    internal val value get() = messageText as CharSequence

    override fun addChangeListener(listener: MessageDraftChangeListener) {
        listeners.update {
            buildSet {
                addAll(it)
                add(listener)
            }
        }
    }

    override fun removeChangeListener(listener: MessageDraftChangeListener) {
        listeners.update {
            it.filterNot { element -> element == listener }.toSet()
        }
    }

    override fun insertText(offset: Int, text: String) {
        insertTextInternal(offset, text)
        triggerTypingIndicator()
        fireMessageElementsChanged()
    }

    override fun removeText(offset: Int, length: Int) {
        removeTextInternal(offset, length)
        triggerTypingIndicator()
        fireMessageElementsChanged()
    }

    override fun insertSuggestedMention(mention: SuggestedMention, text: String) {
        if (messageText.substring(mention.offset, mention.offset + mention.replaceFrom.length) != mention.replaceFrom) {
            throw PubNubException(PubNubErrorMessage.MENTION_SUGGESTION_INVALID)
        }
        removeTextInternal(mention.offset, mention.replaceFrom.length)
        insertTextInternal(mention.offset, text)
        addMentionInternal(mention.offset, text.length, mention.target)
        triggerTypingIndicator()
        fireMessageElementsChanged()
    }

    override fun addMention(offset: Int, length: Int, target: MentionTarget) {
        addMentionInternal(offset, length, target)
        fireMessageElementsChanged()
    }

    override fun removeMention(offset: Int) {
        mentions.removeAll { offset in it.start until it.endExclusive }
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

    private val RegexMatchResult.matchStart get() = range.first

    private fun getSuggestedMentions(): PNFuture<List<SuggestedMention>> {
        val allUserMentions = findUserMentionMatches(messageText)
        val allChannelMentions = findChannelMentionMatches(messageText)

        val userSuggestionsNeededFor = allUserMentions
            .filter { matchResult ->
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
            .filter { matchResult -> matchResult.value.length > 3 }
            .map { matchResult ->
                getSuggestedUsers(matchResult.value.substring(1)).then {
                    it.map { user ->
                        SuggestedMention(matchResult.matchStart, matchResult.value, user.name ?: user.id, MentionTarget.User(user.id))
                    }
                }
            }.awaitAll()
        val getSuggestedChannelsFuture = channelSuggestionsNeededFor
            .filter { matchResult -> matchResult.value.length > 3 }
            .map { matchResult ->
                getSuggestedChannels(matchResult.value.substring(1)).then { channels ->
                    channels.map { channel ->
                        SuggestedMention(
                            matchResult.matchStart,
                            matchResult.value,
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

    private fun addMentionInternal(offset: Int, length: Int, target: MentionTarget) {
        val mention = Mention(offset, length, target)
        mentions.forEach {
            require(
                (mention.start >= it.endExclusive || mention.endExclusive <= it.start)
            ) { "${PubNubErrorMessage.MENTION_CANNOT_INTERSECT} ${it.start} ${it.endExclusive}, ${mention.start} ${mention.endExclusive}" }
        }
        mentions.add(mention)
    }

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
//        private val linkRegex = Regex("""\[(?<text>(?:[^]]*?(?:\\\\)*(?:\\])*)+?)]\((?<link>(?:[^)]*?(?:\\\\)*(?:\\\))*)+?)\)""")
        private val linkRegex = Regex("""\[(?<text>(?:[^\]]*?(?:\\\\)*(?:\\\])*)+?)\]\((?<link>(?:[^)]*?(?:\\\\)*(?:\\\))*)+?)\)""")

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
                val remainingText = plainText.substring(consumedUntil)
                if (remainingText.isNotEmpty()) {
                    add(MessageElement.PlainText(remainingText))
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

internal class Mention(
    var start: Int,
    var length: Int,
    val target: MentionTarget,
) : Comparable<Mention> {
    val endExclusive get() = start + length

    override fun compareTo(other: Mention): Int {
        return start.compareTo(other.start)
    }
}

internal expect interface RegexMatchResult {
    val value: String
    val range: IntRange
}

internal expect fun findUserMentionMatches(input: CharSequence): List<RegexMatchResult>

internal expect fun findChannelMentionMatches(input: CharSequence): List<RegexMatchResult>
