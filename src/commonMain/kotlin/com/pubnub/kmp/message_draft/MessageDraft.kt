package com.pubnub.kmp.message_draft

import com.pubnub.api.PubNubException
import com.pubnub.kmp.Channel
import com.pubnub.kmp.Chat
import com.pubnub.kmp.Message
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.User
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.map
import com.pubnub.kmp.then
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import com.pubnub.kmp.utils.indexOfDifference
import com.pubnub.kmp.utils.isValidUrl
import com.pubnub.kmp.utils.shift
import kotlin.math.abs
import kotlin.properties.Delegates

class MessageDraft(
    private val chat: Chat,
    private val channel: Channel,
    private val userSuggestionDataSource: UserSuggestionDataSource,
    private val userSuggestionLimit: Int
) {
    private var currentTextLinkDescriptors: MutableList<TextLinkDescriptor> = emptyList<TextLinkDescriptor>().toMutableList()
    private var currentText: String by Delegates.observable("") { _, oldValue, newValue -> onTextChanged(oldValue, newValue) }
    private var quotedMessage: Message? = null
    private var mentionedUsers: MutableList<MentionedItemDescriptor<User>> = emptyList<MentionedItemDescriptor<User>>().toMutableList()
    private var mentionedChannels: MutableList<MentionedItemDescriptor<Channel>> = emptyList<MentionedItemDescriptor<Channel>>().toMutableList()
    private var getUsersFuture: PNFuture<List<User>> = emptyList<User>().asFuture()
    private var getChannelsFuture: PNFuture<List<Channel>> = emptyList<Channel>().asFuture()

    var onChange: (String, SuggestedMessageDraftMentions) -> Unit = { _, _  -> }

    init {
        require(userSuggestionLimit <= 100) { "Fetching more than 100 users is prohibited" }
    }
    fun addLinkedText(text: String, link: String, positionInInput: Int) {
        val textLinkDescriptor = TextLinkDescriptor(
            range = positionInInput..<positionInInput + text.length,
            text = text,
            link = link
        )

        if (link.isValidUrl()) {
            throw PubNubException("Invalid url $link")
        }
        if (textLinkDescriptor.overlaps(currentTextLinkDescriptors)) {
            throw PubNubException("You cannot insert a link inside another link")
        }

        currentText = currentText.replaceRange(positionInInput..<link.length, text)
        currentTextLinkDescriptors.add(textLinkDescriptor)
    }

    fun removeLinkedText(positionInInput: Int) {
        currentTextLinkDescriptors.find { it.range.contains(positionInInput) }?.let { currentTextLinkDescriptors.remove(it) }
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
        val mentionedItemDescriptor = MentionedItemDescriptor(
            item = user,
            id = user.id,
            name = user.name.orEmpty(),
            range = positionInInput..<positionInInput + user.name.orEmpty().length
        )
        addMentionedItem(
            prefix = "@",
            mentionedValue = mentionedItemDescriptor,
            toCollection = mentionedUsers,
            atPosition = positionInInput
        )
    }

    fun addMentionedChannel(channel: Channel, positionInInput: Int) {
        val mentionedItemDescriptor = MentionedItemDescriptor(
            item = channel,
            id = channel.id,
            name = channel.name.orEmpty(),
            range = positionInInput..<positionInInput + channel.name.orEmpty().length
        )
        addMentionedItem(
            prefix = "#",
            mentionedValue = mentionedItemDescriptor,
            toCollection = mentionedChannels,
            atPosition = positionInInput
        )
    }

    fun getHighlightedMention(selectionStart: Int): HighlightedUser? {
        return mentionedUsers.firstOrNull { it.range.contains(selectionStart) }?.let {
            HighlightedUser(user = it.item, nameOccurrenceIndex = it.range.first)
        }
    }

    fun getMessagePreview(): MessageDraftPreview {
        return MessageDraftPreview(
            text = currentText,
            mentionedUsers = mentionedUsers.associateBy { it.range.first }.mapValues { MessageMentionedUser(it.value.id, it.value.name) },
            referencedChannels = mentionedChannels.associateBy { it.range.first }.mapValues { MessageReferencedChannel(it.value.id, it.value.name) },
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
            mentionedUsers = mentionedUsers.associateBy { it.range.first }.mapValues { MessageMentionedUser(it.value.id, it.value.name) },
            referencedChannels = mentionedChannels.associateBy { it.range.first }.mapValues { MessageReferencedChannel(it.value.id, it.value.name) },
            textLinks = currentTextLinkDescriptors.map { TextLink(it.range.first, it.range.last, it.link) },
            quotedMessage = quotedMessage
        )
    }

    private fun onTextChanged(oldValue: String, newValue: String) {
        newValue.indexOfDifference(oldValue)?.let {
            val oldTxtLength = oldValue.length
            val newTxtLength = newValue.length
            val textLengthDiff = abs(newTxtLength - oldTxtLength)
            val diffToApply = if (newTxtLength > oldTxtLength) { textLengthDiff } else { -textLengthDiff }

            removeNoLongerExistingItems()
            shiftRanges(it, diffToApply)
            getSuggestedUsersAndChannels(it)
        }
    }

    private fun shiftRanges(indexOfDiff: Int, diffToApply: Int) {
        currentTextLinkDescriptors.filter { it.range.first >= indexOfDiff || it.range.contains(indexOfDiff) }.forEach { link ->
            if (link.range.contains(indexOfDiff)) {
                link.shiftRange(byStartOffset = 0, byEndOffset = diffToApply)
            } else {
                link.shiftRange(byStartOffset = diffToApply, byEndOffset = diffToApply)
            }
        }
        currentTextLinkDescriptors.removeAll {
            it.range.isEmpty()
        }
        mentionedUsers.forEach {
            it.shiftRange(diffToApply, diffToApply)
        }
        mentionedChannels.forEach {
            it.shiftRange(diffToApply, diffToApply)
        }
    }

    private fun removeNoLongerExistingItems() {
        mentionedUsers.removeAll {
            !currentText.contains("@" + it.item.name.orEmpty())
        }
        mentionedChannels.removeAll {
            !currentText.contains("#" + it.item.name.orEmpty())
        }
        currentTextLinkDescriptors.removeAll {
            !currentText.contains(it.text)
        }
    }

    private fun getSuggestedUsersAndChannels(indexOfDiff: Int) {
        val userMention = findNameToQuery(mentionedUsers, "@", indexOfDiff)
        val channelMention = findNameToQuery(mentionedChannels, "#", indexOfDiff)

        getUsersFuture = if (userMention.first != null && userMention.second != null) {
            when (userSuggestionDataSource) {
                UserSuggestionDataSource.CHANNEL ->
                    channel.getMembers(
                        filter = "uuid.name LIKE `${userMention.first}`",
                        limit = userSuggestionLimit
                    ).map {
                        it.members.map { membership -> membership.user }
                    }
                UserSuggestionDataSource.CHAT ->
                    chat.getUsers(
                        filter = "uuid.name LIKE `${userMention.first}`",
                        limit = 10
                    ).map {
                        it.users.toList()
                    }
            }
        } else {
            emptyList<User>().asFuture()
        }

        getChannelsFuture = if (channelMention.first != null && channelMention.second != null) {
            chat.getChannelSuggestions(text = currentText, filter = "name LIKE `${channelMention.first}`", limit = 10)
        } else {
            emptyList<Channel>().asFuture()
        }

        awaitAll(getUsersFuture, getChannelsFuture).then {
            invokeOnChangeCallback(
                userMentionedAt = userMention.second,
                users = it.first.toList(),
                channelMentionedAt = channelMention.second,
                channels = it.second
            )
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
            onChange(currentText, suggestedMentions)
        }
    }

    private fun <T> findNameToQuery(
        alreadyMentionedItemDescriptors: List<MentionedItemDescriptor<T>>,
        prefix: String,
        indexOfDiff: Int
    ): Pair<String?, Int?> {
        val affectedAlreadyMentionedItem = alreadyMentionedItemDescriptors.firstOrNull {
            it.range.contains(indexOfDiff)
        }
        val matchResults = if (affectedAlreadyMentionedItem != null) {
            findAllMentions(prefix, indexOfDiff)
        } else {
            findAllMentions(prefix)
        }.filter {
            !alreadyMentionedItemDescriptors.map { item -> item.name }.contains(it.value.drop(1))
        }
        return if (affectedAlreadyMentionedItem != null) {
            Pair(matchResults.firstOrNull()?.value, matchResults.firstOrNull()?.range?.start)
        } else {
            Pair(matchResults.lastOrNull()?.value, matchResults.lastOrNull()?.range?.start)
        }
    }

    private fun findAllMentions(withPrefix: String, startIndex: Int = 0): Sequence<MatchResult> {
        return "$withPrefix[\\w\\s]+".toRegex().findAll(currentText, startIndex).filter { it.value.length >= 3 }
    }

    private fun findFirstMention(withPrefix: String, startIndex: Int = 0): MatchResult? {
        return "$withPrefix[\\w\\s]+".toRegex().find(currentText, startIndex)
    }

    private fun <T> addMentionedItem(
        prefix: String,
        mentionedValue: MentionedItemDescriptor<T>,
        toCollection: MutableList<MentionedItemDescriptor<T>>,
        atPosition: Int
    ) {
        findFirstMention(prefix, atPosition)?.let {
            if (mentionedValue.name.startsWith(it.value)) {
                toCollection.add(mentionedValue)
                currentText = currentText.replaceRange(it.range, mentionedValue.name)
            }
        }
    }
}

private class MentionedItemDescriptor<T>(
    val item: T,
    val id: String,
    val name: String,
    var range: IntRange
) {
    fun shiftRange(byStartOffset: Int, byEndOffset: Int) {
        range = range.shift(byStartOffset, byEndOffset)
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

    fun shiftRange(byStartOffset: Int, byEndOffset: Int) {
        range = range.shift(byStartOffset, byEndOffset)
    }
}
