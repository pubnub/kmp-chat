package com.pubnub.kmp.message_draft

import com.pubnub.api.PubNubException
import com.pubnub.kmp.Channel
import com.pubnub.kmp.Chat
import com.pubnub.kmp.Message
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.User
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.then
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import com.pubnub.kmp.utils.indexOfDifference
import com.pubnub.kmp.utils.isValidUrl
import kotlin.properties.Delegates

class MessageDraft(
    private val chat: Chat,
    private val channel: Channel,
    private val userSuggestionDataSource: UserSuggestionDataSource,
    private val userSuggestionLimit: Int = 10
) {
    private var currentTextLinkDescriptors: MutableList<TextLinkDescriptor> = emptyList<TextLinkDescriptor>().toMutableList()
    private var quotedMessage: Message? = null
    private var mentionedUsers: MutableList<MentionDescriptor<User>> = emptyList<MentionDescriptor<User>>().toMutableList()
    private var mentionedChannels: MutableList<MentionDescriptor<Channel>> = emptyList<MentionDescriptor<Channel>>().toMutableList()
    private var getUsersFuture: PNFuture<List<User>> = emptyList<User>().asFuture()
    private var getChannelsFuture: PNFuture<List<Channel>> = emptyList<Channel>().asFuture()

    var onSuggestionsChanged: (String, SuggestedMessageDraftMentions) -> Unit = { _, _  -> }
    var currentText: String by Delegates.observable("") { _, oldValue, newValue -> onTextChanged(oldValue, newValue) }

    init {
        require(userSuggestionLimit <= 100) { "Fetching more than 100 users is prohibited" }
    }
    fun addLinkedText(text: String, link: String, positionInInput: Int) {
        val textLinkDescriptor = TextLinkDescriptor(
            range = positionInInput..positionInInput + text.length,
            text = text,
            link = link
        )

        if (!link.isValidUrl()) {
            throw PubNubException("You need to insert a URL")
        }
        if (textLinkDescriptor.overlaps(currentTextLinkDescriptors)) {
            throw PubNubException("You cannot insert a link inside another link")
        }

        currentTextLinkDescriptors.add(textLinkDescriptor)

        val before = currentText.substring(0, positionInInput)
        val after = currentText.substring(positionInInput)
        val newString = before + after.replaceFirst(link, text)

        currentText = newString
    }

    fun removeLinkedText(positionInInput: Int) {
        if (!currentTextLinkDescriptors.removeAll {
            it.range.contains(positionInInput)
        }) {
            println("This operation is noop. There is no link at this position.")
        }
    }

    fun addQuote(message: Message) {
        if (message.channelId != channel.id) {
            throw PubNubException("Cannot quote message from other channels")
        } else {
            quotedMessage = message
        }
    }

    fun removeQuote() {
        quotedMessage = null
    }

    fun addMentionedUser(user: User, positionInInput: Int) {
        if (!addMention(user, user.id, user.name.orEmpty(), "@", positionInInput, mentionedUsers)) {
            throw PubNubException("The user ${channel.name.orEmpty()} doesn't appear in the text")
        }
    }

    fun removeMentionedUser(positionInInput: Int) {
       if (!mentionedUsers.removeAll {
           it.fullMentionRange.first == positionInInput
       }) {
           println("This is noop. There is no mention occurrence at $positionInInput index")
       }
    }

    fun addMentionedChannel(channel: Channel, positionInInput: Int) {
        if (!addMention(channel, channel.id, channel.name.orEmpty(), "#", positionInInput, mentionedChannels)) {
            throw PubNubException("The channel ${channel.name.orEmpty()} doesn't appear in the text")
        }
    }

    fun removeMentionedChannel(positionInInput: Int) {
        if (!mentionedChannels.removeAll {
            it.fullMentionRange.first == positionInInput
        }) {
            println("This is noop. There is no channel occurrence at $positionInInput index")
        }
    }

    fun getHighlightedUserMention(selectionStart: Int): HighlightedUser? {
        return mentionedUsers.firstOrNull { it.fullMentionRange.contains(selectionStart) }?.let {
            HighlightedUser(user = it.item, nameOccurrenceIndex = it.fullMentionRange.first)
        }
    }

    fun getMessagePreview(): MessageDraftPreview {
        return MessageDraftPreview(
            text = currentText,
            mentionedUsers = mentionedUsers.associateBy { it.fullMentionRange.first }.mapValues { MessageMentionedUser(it.value.id, it.value.fullMentionName.drop(1)) },
            mentionedChannels = mentionedChannels.associateBy { it.fullMentionRange.first }.mapValues { MessageReferencedChannel(it.value.id, it.value.fullMentionName.drop(1)) },
            textLinks = currentTextLinkDescriptors.map { TextLink(it.range.first, it.range.last, it.link) },
            quotedMessage = quotedMessage
        )
    }

    fun send(
        shouldStore: Boolean?,
        usePost: Boolean?,
        ttl: Int?,
        meta: Map<String, Any>?
    ) {
        channel.sendText(
            text = currentText,
            shouldStore = shouldStore,
            usePost = usePost ?: false,
            ttl = ttl,
            meta = meta,
            mentionedUsers = mentionedUsers.associateBy { it.fullMentionRange.first }.mapValues { MessageMentionedUser(it.value.id, it.value.fullMentionName) },
            referencedChannels = mentionedChannels.associateBy { it.fullMentionRange.first }.mapValues { MessageReferencedChannel(it.value.id, it.value.fullMentionName) },
            textLinks = currentTextLinkDescriptors.map { TextLink(it.range.first, it.range.last, it.link) },
            quotedMessage = quotedMessage
        )
    }

    private fun onTextChanged(oldValue: String, newValue: String) {
        newValue.indexOfDifference(oldValue)?.let { diffIndex ->
            val existingUserMentionAtDiffIdx = updateMentionDescriptors(mentionedUsers.iterator(), diffIndex).firstOrNull {
                it.fullMentionRange.contains(diffIndex)
            }
            val existingChannelMentionAtDiffIdx = updateMentionDescriptors(mentionedChannels.iterator(), diffIndex).firstOrNull {
                it.fullMentionRange.contains(diffIndex)
            }
            val bestUserMentionToQuery = existingUserMentionAtDiffIdx?.let {
                findFirstMention("@", it.fullMentionRange.first, 3)?.let { matchResult ->
                    Pair(matchResult.value, matchResult.range.first)
                }
            } ?: findNameToQuery(
                currentMentions = mentionedUsers,
                prefix = "@",
                greaterOrEqualThan = 3
            )

            val bestChannelMentionToQuery = existingChannelMentionAtDiffIdx?.let {
                findFirstMention("#", it.fullMentionRange.first, 3)?.let { matchResult ->
                    Pair(matchResult.value, matchResult.range.first)
                }
            } ?: findNameToQuery(
                currentMentions = mentionedChannels,
                prefix = "#",
                greaterOrEqualThan = 3
            )

            updateTextLinkDescriptors(
                indexOfDiff = diffIndex,
                txtLengthDiff = newValue.length - oldValue.length
            )
            getSuggestedUsersAndChannels(
                bestUserMentionToQuery,
                bestChannelMentionToQuery
            )
        }
    }

    private fun<T> updateMentionDescriptors(withIterator: MutableIterator<MentionDescriptor<T>>, indexOfDiff: Int): List<MentionDescriptor<T>> {
        val removedElements = emptyList<MentionDescriptor<T>>().toMutableList()

        while (withIterator.hasNext()) {
            val it = withIterator.next()
            val startIndex = currentText.indexOf(it.fullMentionName)
            val endIndex = if (startIndex == -1 ) { -1 } else { startIndex + it.fullMentionName.length }

            if (it.fullMentionRange.first >= indexOfDiff || it.fullMentionRange.contains(indexOfDiff)) {
                if (!(startIndex..<endIndex).isEmpty()) {
                    it.updateRange(startIndex..<endIndex)
                } else {
                    removedElements.add(it)
                    withIterator.remove()
                }
            }
        }
        return removedElements
    }

    private fun updateTextLinkDescriptors(indexOfDiff: Int, txtLengthDiff: Int): List<TextLinkDescriptor> {
        val iterator = currentTextLinkDescriptors.iterator()
        val removedElements = emptyList<TextLinkDescriptor>().toMutableList()

        while (iterator.hasNext()) {
            val it = iterator.next()
            val startIndex = currentText.indexOf(it.text)

            if (startIndex == -1) {
                removedElements.add(it)
                iterator.remove()
            } else if (it.range.first > indexOfDiff) {
                it.updateRange(it.range.first + txtLengthDiff..<it.range.last + txtLengthDiff)
            } else {
                // Text alteration occurred before index of diff. No need to reindex currently iterated text link"
            }
        }
        return removedElements
    }

    private fun getSuggestedUsersAndChannels(userMention: Pair<String, Int>?, channelMention: Pair<String, Int>?) {
        getUsersFuture = if (userMention != null) {
            when (userSuggestionDataSource) {
                UserSuggestionDataSource.CHANNEL ->
                    channel.getMembers(
                        filter = "uuid.name LIKE `${userMention.first}`",
                        limit = userSuggestionLimit
                    ).then {
                        it.members.map { membership -> membership.user }
                    }
                UserSuggestionDataSource.CHAT ->
                    chat.getUsers(
                        filter = "uuid.name LIKE `${userMention.first}`",
                        limit = 10
                    ).then {
                        it.users.toList()
                    }
            }
        } else {
            emptyList<User>().asFuture()
        }

        getChannelsFuture = if (channelMention != null) {
            chat.getChannelSuggestions(filter = "name LIKE `${channelMention.first}`", limit = 10)
        } else {
            emptyList<Channel>().asFuture()
        }

        awaitAll(getUsersFuture, getChannelsFuture).async {
            it.onSuccess { resultValue ->
                invokeOnChangeCallback(
                    userMentionedAt = userMention?.second,
                    users = resultValue.first.toList(),
                    channelMentionedAt = channelMention?.second,
                    channels = resultValue.second
                )
            }
        }
    }

    private fun invokeOnChangeCallback(
        userMentionedAt: Int?,
        users: List<User>,
        channelMentionedAt: Int?,
        channels: List<Channel>
    ) {
        val suggestedMentions = SuggestedMessageDraftMentions.create(
            userMentionedAt,
            users,
            channelMentionedAt,
            channels
        )
        if (suggestedMentions.channels.isNotEmpty() || suggestedMentions.users.isNotEmpty()) {
            onSuggestionsChanged(currentText, suggestedMentions)
        }
    }

    private fun<T> addMention(
        mention: T,
        id: String,
        name: String,
        withPrefix: String,
        positionInInput: Int,
        intoCollection: MutableList<MentionDescriptor<T>>
    ): Boolean {
        if (positionInInput >= currentText.length) {
            return false
        }
        val matchRes = findFirstMention(
           withPrefix = withPrefix,
           startIndex = positionInInput,
           greaterOrEqualThan = 3
        )?.also {
            val extractedNameWithoutPrefix = name.startsWith(it.value.drop(withPrefix.length))
            val matchesRangePosition = it.range.first == positionInInput

            if (extractedNameWithoutPrefix.and(matchesRangePosition)) {
                val fullMentionName = withPrefix + name
                val fullMentionRange = it.range.first..it.range.first + name.length
                intoCollection.add(MentionDescriptor(id, mention, fullMentionName, fullMentionRange))
                currentText = currentText.replaceRange(it.range, fullMentionName)
            }
        }

        return (matchRes != null)
    }

    private fun <T> findNameToQuery(
        currentMentions: List<MentionDescriptor<T>>,
        prefix: String,
        greaterOrEqualThan: Int
    ): Pair<String, Int>? {
        val currentMentionsRanges = currentMentions.map {
            it.fullMentionRange
        }
        "$prefix(\\w+)".toRegex().findAll(currentText).forEach { matchResult ->
            if (currentMentionsRanges.intersect(matchResult.range).isEmpty()) {
                if (matchResult.value.drop(prefix.length).length >= greaterOrEqualThan) {
                    return Pair(matchResult.value.drop(prefix.length), matchResult.range.first)
                }
            }
        }
        return null
    }

    private fun findFirstMention(withPrefix: String, startIndex: Int, greaterOrEqualThan: Int): MatchResult? {
        return "$withPrefix(\\w+)".toRegex().find(currentText, startIndex)?.let {
            return if (it.value.length >= greaterOrEqualThan) { it } else { null }
        }
    }
}

private class MentionDescriptor<T> {
    val id: String
    val item: T
    val fullMentionName: String
    var fullMentionRange: IntRange private set
    constructor(id: String, item: T, fullMentionName: String, fullMentionRange: IntRange) {
        this.id = id
        this.item = item
        this.fullMentionName = fullMentionName
        this.fullMentionRange = fullMentionRange
    }
    fun updateRange(newRange: IntRange) {
        fullMentionRange = newRange
    }
}

private class TextLinkDescriptor {
    val link: String
    val text: String
    var range: IntRange private set

    constructor(link: String, text: String, range: IntRange) {
        this.link = link
        this.text = text
        this.range = range
    }

    fun overlaps(otherLinks: List<TextLinkDescriptor>): Boolean {
        return otherLinks.any {
            it.range.contains(range.first) || it.range.contains(range.last)
        }
    }

    fun updateRange(newRange: IntRange) {
        range = newRange
    }
}
