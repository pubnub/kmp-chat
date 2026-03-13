@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.api.PubNubException
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.UploadableImpl
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import com.pubnub.kmp.toMap
import kotlin.js.Promise
import kotlin.math.abs

object Validator {
    private val validProtocols = listOf("http://", "https://", "www.")
    private val urlRegex =
        """(https?:\/\/(?:www\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|www\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|https?:\/\/(?:www\.|(?!www))[a-zA-Z0-9]+\.[^\s]{2,}|www\.[a-zA-Z0-9]+\.[^\s]{2,})""".toRegex(
            RegexOption.IGNORE_CASE
        )

    fun isUrl(potentialUrl: String): Boolean {
        if (validProtocols.none { potentialUrl.startsWith(it) }) {
            return false
        }

        if (potentialUrl.split(".").filter { it.isNotEmpty() }.size <
            if (potentialUrl.startsWith("www.")) {
                3
            } else {
                2
            }
        ) {
            return false
        }

        return urlRegex.matches(potentialUrl.replace("\n", ""))
    }
}

fun range(start: Int, stop: Int, step: Int = 1): List<Int> {
    return (start..stop step step).toList()
}

@JsExport
@JsName("MessageDraftV1")
class MessageDraftV1Js(private val chat: ChatJs, private val channel: ChannelJs, config: MessageDraftConfig? = null) {
    private var previousValue = ""
    private val mentionedUsers: MutableMap<Int, UserJs> = mutableMapOf()
    private val referencedChannels: MutableMap<Int, ChannelJs> = mutableMapOf()
    private val _textLinks: MutableList<TextLink> = mutableListOf()

    var value = ""
    val textLinks get() = _textLinks.toTypedArray()
    var quotedMessage: MessageJs? = null
    val config: MessageDraftConfig = createJsObject<MessageDraftConfig> {
        this.userSuggestionSource = config?.userSuggestionSource ?: "channel"
        this.isTypingIndicatorTriggered = config?.isTypingIndicatorTriggered ?: true
        this.userLimit = config?.userLimit ?: 10
        this.channelLimit = config?.channelLimit ?: 10
    }
    var files: Any? = null // Adjust type as needed

    private fun reindexTextLinks() {
        if (value.startsWith(previousValue)) {
            // a user keeps adding text to the end; nothing to reindex
            return
        }
        if (value == previousValue) {
            // nothing changed so there is nothing to reindex
            return
        }
        val lengthDifference = abs(previousValue.length - value.length)

        var newLinks = _textLinks.toMutableList()
        val indicesToFilterOut: MutableList<Int> = mutableListOf()

        // cut from the end
        if (previousValue.startsWith(value)) {
            val differenceStartsAtIndex = value.length

            newLinks.forEachIndexed { i, textLink ->
                if (textLink.endIndex < differenceStartsAtIndex) {
                    return@forEachIndexed
                }
                // this word was cut
                if (textLink.startIndex >= differenceStartsAtIndex) {
                    indicesToFilterOut.add(i)
                    return@forEachIndexed
                }
                // second part of this word was cut
                if (textLink.startIndex < differenceStartsAtIndex) {
                    newLinks[i] = newLinks[i].copy(endIndex = value.length)
                }
            }

            newLinks =
                newLinks.filterIndexed { linkIndex, _ -> !indicesToFilterOut.contains(linkIndex) }.toMutableList()
            _textLinks.clear()
            _textLinks.addAll(newLinks)
        }

        // a user cut text from the beginning
        else if (previousValue.endsWith(value)) {
            newLinks = _textLinks.toMutableList()
            indicesToFilterOut.clear()
            val differenceEndsAtIndex = lengthDifference

            newLinks.forEachIndexed { i, textLink ->
                // this word is intact
                if (textLink.startIndex >= differenceEndsAtIndex) {
                    newLinks[i] = newLinks[i].copy(
                        startIndex = newLinks[i].startIndex - lengthDifference,
                        endIndex = newLinks[i].endIndex - lengthDifference
                    )
                    return@forEachIndexed
                }
                // this word was cut
                if (textLink.endIndex <= differenceEndsAtIndex) {
                    indicesToFilterOut.add(i)
                    return@forEachIndexed
                }
                // first part of this word was cut
                if (textLink.startIndex < differenceEndsAtIndex) {
                    newLinks[i] = newLinks[i].copy(startIndex = 0, endIndex = newLinks[i].endIndex - lengthDifference)
                }
            }
            newLinks =
                newLinks.filterIndexed { linkIndex, _ -> !indicesToFilterOut.contains(linkIndex) }.toMutableList()
            _textLinks.clear()
            _textLinks.addAll(newLinks)
        }

        // a user cut from the middle of the text
        else if (previousValue.length > value.length) {
            newLinks = _textLinks.toMutableList()
            indicesToFilterOut.clear()
            var differenceStartsAtIndex = -1
            var differenceEndsAtIndex = -1

            for ((index, letter) in previousValue.withIndex()) {
                if (value.getOrElse(index) { ' ' } != letter && differenceStartsAtIndex == -1) {
                    differenceStartsAtIndex = index
                }
                if (
                    value.getOrElse(value.length - 1 - index) { ' ' } !=
                    previousValue.getOrElse(previousValue.length - 1 - index) { ' ' } &&
                    differenceEndsAtIndex == -1
                ) {
                    differenceEndsAtIndex = previousValue.length - index
                }
            }

            newLinks.forEachIndexed { i, textLink ->
                // this word was cut
                if (
                    differenceStartsAtIndex <= textLink.startIndex &&
                    differenceEndsAtIndex >= textLink.endIndex
                ) {
                    indicesToFilterOut.add(i)
                    return@forEachIndexed
                }
                // the middle part of this word was cut
                if (
                    differenceStartsAtIndex > textLink.startIndex &&
                    differenceEndsAtIndex < textLink.endIndex
                ) {
                    newLinks[i] = newLinks[i].copy(endIndex = newLinks[i].endIndex - lengthDifference)
                    return@forEachIndexed
                }
                // second part of this word was cut
                if (
                    differenceStartsAtIndex >= textLink.startIndex &&
                    differenceEndsAtIndex >= textLink.endIndex &&
                    differenceStartsAtIndex < textLink.endIndex
                ) {
                    newLinks[i] = newLinks[i].copy(endIndex = differenceStartsAtIndex)
                    return@forEachIndexed
                }
                // first part of this word was cut
                if (
                    differenceEndsAtIndex > textLink.startIndex &&
                    differenceStartsAtIndex <= textLink.startIndex
                ) {
                    newLinks[i] = newLinks[i].copy(
                        endIndex = newLinks[i].endIndex - lengthDifference,
                        startIndex = differenceStartsAtIndex
                    )
                    return@forEachIndexed
                }
                // this word is intact
                if (differenceEndsAtIndex < textLink.endIndex) {
                    newLinks[i] = newLinks[i].copy(
                        startIndex = newLinks[i].startIndex - lengthDifference,
                        endIndex = newLinks[i].endIndex - lengthDifference
                    )
                    return@forEachIndexed
                }
            }

            newLinks =
                newLinks.filterIndexed { linkIndex, _ -> !indicesToFilterOut.contains(linkIndex) }.toMutableList()
            _textLinks.clear()
            _textLinks.addAll(newLinks)
        }
        // a user keeps adding text to the beginning
        else if (value.endsWith(previousValue)) {
            newLinks = _textLinks.toMutableList()
            indicesToFilterOut.clear()

            newLinks.forEachIndexed { i, newLink ->
                newLinks[i] = newLinks[i].copy(
                    endIndex = newLinks[i].endIndex + lengthDifference,
                    startIndex = newLinks[i].startIndex + lengthDifference
                )
            }

            _textLinks.clear()
            _textLinks.addAll(newLinks)
        }
        // a user keeps adding text in the middle
        else if (value.length > previousValue.length) {
            newLinks = _textLinks.toMutableList()
            indicesToFilterOut.clear()
            var differenceStartsAtIndex = -1
            var differenceEndsAtIndex = -1

            for ((index, letter) in previousValue.withIndex()) {
                if (value.getOrElse(index) { ' ' } != letter && differenceStartsAtIndex == -1) {
                    differenceStartsAtIndex = index
                }
                if (
                    value.getOrElse(value.length - 1 - index) { ' ' } !=
                    previousValue.getOrElse(previousValue.length - 1 - index) { ' ' } &&
                    differenceEndsAtIndex == -1
                ) {
                    differenceEndsAtIndex = previousValue.length - index
                }
            }

            newLinks.forEachIndexed { i, textLink ->
                // text was added before this link
                if (differenceEndsAtIndex <= textLink.startIndex) {
                    newLinks[i] = newLinks[i].copy(
                        startIndex = newLinks[i].startIndex + lengthDifference,
                        endIndex = newLinks[i].endIndex + lengthDifference
                    )
                    return@forEachIndexed
                }
                // text was added in the middle of the link
                if (
                    differenceStartsAtIndex > textLink.startIndex &&
                    differenceEndsAtIndex < textLink.endIndex
                ) {
                    newLinks[i] = newLinks[i].copy(endIndex = newLinks[i].endIndex + lengthDifference)
                    return@forEachIndexed
                }
                if (
                    differenceStartsAtIndex <= textLink.startIndex &&
                    differenceEndsAtIndex >= textLink.endIndex
                ) {
                    indicesToFilterOut.add(i)
                    return@forEachIndexed
                }
            }
            newLinks =
                newLinks.filterIndexed { linkIndex, _ -> !indicesToFilterOut.contains(linkIndex) }.toMutableList()
            _textLinks.clear()
            _textLinks.addAll(newLinks)
        }
    }

    private fun getUserOrChannelReference(
        splitSymbol: String,
        referencesObject: Map<Int, Any>,
    ): UserOfChannelReference {
        val copiedObject = referencesObject.toMutableMap()
        val previousWordsStartingWithSymbol = previousValue.split(" ").filter { it.startsWith(splitSymbol) }
        val currentWordsStartingWithSymbol = value.split(" ").filter { it.startsWith(splitSymbol) }

        var differentReferencePosition = -1
        var differentReference: String? = null

        for (i in currentWordsStartingWithSymbol.indices) {
            if (currentWordsStartingWithSymbol[i] != previousWordsStartingWithSymbol.getOrElse(i) { "" }) {
                differentReference = currentWordsStartingWithSymbol[i]
                differentReferencePosition = i
                break
            }
        }

        if (previousWordsStartingWithSymbol.size > currentWordsStartingWithSymbol.size) {
            // a mention was removed
            val firstRemovalIndex =
                previousWordsStartingWithSymbol.indexOfFirst { e -> !currentWordsStartingWithSymbol.contains(e) }
            val lastRemovalIndex =
                previousWordsStartingWithSymbol.indexOfLast { e -> !currentWordsStartingWithSymbol.contains(e) }

            if (lastRemovalIndex != -1) {
                val reindexedReferences = copiedObject.toMutableMap()

                copiedObject.forEach { (key, _) ->
                    if (key >= firstRemovalIndex && key <= lastRemovalIndex) {
                        reindexedReferences.remove(key)
                    }
                    if (key > lastRemovalIndex) {
                        val newValue = copiedObject[key]
                        reindexedReferences.remove(key)
                        reindexedReferences[key - lastRemovalIndex + firstRemovalIndex - 1] = newValue!!
                    }
                }

                copiedObject.clear()
                copiedObject.putAll(reindexedReferences)
            }
        }

        copiedObject.forEach { (key, value) ->
            val referencedName = when (value) {
                is UserJs -> value.name.orEmpty()
                is ChannelJs -> value.name.orEmpty()
                else -> error("Not going to happen")
            }

            if (referencedName.isNotEmpty() && currentWordsStartingWithSymbol.getOrElse(key) { "" }.isEmpty()) {
                copiedObject.remove(key)
            }

            val splitSymbolRegex = if (splitSymbol == "@") {
                """(^|\s)@([^\s@]+(?:\s+[^\s@]+)*)""".toRegex()
            } else {
                """(^|\s)#([^\s#]+(?:\s+[^\s#]+)*)""".toRegex()
            }

            val splitMentionsByAt =
                (splitSymbolRegex.findAll(this.value).map { it.value }.toList()).map { it.trim().substring(1) }

            if (referencedName.isNotEmpty() && !splitMentionsByAt.getOrElse(key) { "" }.startsWith(referencedName)) {
                copiedObject.remove(key)
            }
        }

        return UserOfChannelReference(
            referencesObject = copiedObject,
            differentReference = differentReference,
            differentReferencePosition = differentReferencePosition,
        )
    }

    private fun parseTextToGetSuggestedUser(): Promise<JsMap<Any>> {
        val result = getUserOrChannelReference("@", mentionedUsers)
        val differentReference = result.differentReference
        val differentReferencePosition = result.differentReferencePosition
        val referencesObject = result.referencesObject

        mentionedUsers.clear()
        mentionedUsers.putAll(referencesObject.mapValues { it.value as UserJs })

        if (differentReference == null) {
            return Promise.resolve(
                mapOf(
                    "nameOccurrenceIndex" to -1,
                    "suggestedUsers" to arrayOf<UserJs>()
                ).toJsMap()
            )
        }

        val limitOption = config.userLimit?.let { userLimit -> createJsObject<GetSuggestionsParams> { limit = userLimit } }
        val suggestedUsers = if (config.userSuggestionSource == "channel") {
            channel.getUserSuggestions(differentReference, limitOption).then { it.map { it.user }.toTypedArray() }
        } else {
            chat.getUserSuggestions(differentReference, limitOption)
        }

        return suggestedUsers.then { users ->
            mapOf(
                "nameOccurrenceIndex" to differentReferencePosition,
                "suggestedUsers" to users
            ).toJsMap()
        }
    }

    private fun parseTextToGetSuggestedChannels(): Promise<JsMap<Any>> {
        val result = getUserOrChannelReference("#", referencedChannels)
        val differentReference = result.differentReference
        val differentReferencePosition = result.differentReferencePosition
        val referencesObject = result.referencesObject

        referencedChannels.clear()
        referencedChannels.putAll(referencesObject.mapValues { it.value as ChannelJs })

        if (differentReference == null) {
            return Promise.resolve(
                mapOf(
                    "channelOccurrenceIndex" to -1,
                    "suggestedChannels" to arrayOf<ChannelJs>()
                ).toJsMap()
            )
        }

        val limitOption = config.channelLimit?.let { channelLimit -> createJsObject<GetSuggestionsParams> { limit = channelLimit } }
        val suggestedChannels = chat.getChannelSuggestions(differentReference, limitOption)

        return suggestedChannels.then { channels ->
            mapOf(
                "channelOccurrenceIndex" to differentReferencePosition,
                "suggestedChannels" to channels
            ).toJsMap()
        }
    }

    fun onChange(text: String): Promise<JsMap<Any>> {
        previousValue = value
        value = text

        if (config.isTypingIndicatorTriggered ?: false) {
            if (value.isNotEmpty()) {
                channel.startTyping()
            } else {
                channel.stopTyping()
            }
        }

        reindexTextLinks()
        val usersPromise = parseTextToGetSuggestedUser()
        val channelsPromise = parseTextToGetSuggestedChannels()

        return Promise.all(arrayOf(usersPromise, channelsPromise)).then { (users, channels) ->
            mapOf(
                "users" to users,
                "channels" to channels
            ).toJsMap()
        }
    }

    fun addMentionedUser(user: UserJs, nameOccurrenceIndex: Int) {
        var counter = 0
        var result = ""
        var isUserFound = false

        value.split(" ").forEach { word ->
            if (!word.startsWith("@")) {
                result += "$word "
            } else {
                if (counter != nameOccurrenceIndex) {
                    result += "$word "
                } else {
                    val lastCharacter = word.last()
                    result += "@${user.name}"
                    if (listOf('!', '?', '.', ',').contains(lastCharacter)) {
                        result += "$lastCharacter "
                    } else {
                        result += " "
                    }

                    mentionedUsers[nameOccurrenceIndex] = user
                    isUserFound = true
                }
                counter++
            }
        }

        if (!isUserFound) {
            throw Exception("This user does not appear in the text")
        }

        value = result.trim()
    }

    fun addReferencedChannel(channel: ChannelJs, channelNameOccurrenceIndex: Int) {
        var counter = 0
        var result = ""
        var isChannelFound = false

        value.split(" ").forEach { word ->
            if (!word.startsWith("#")) {
                result += "$word "
            } else {
                if (counter != channelNameOccurrenceIndex) {
                    result += "$word "
                } else {
                    val lastCharacter = word.last()
                    result += "#${channel.name}"
                    if (listOf('!', '?', '.', ',').contains(lastCharacter)) {
                        result += "$lastCharacter "
                    } else {
                        result += " "
                    }
                    referencedChannels[channelNameOccurrenceIndex] = channel
                    isChannelFound = true
                }
                counter++
            }
        }

        if (!isChannelFound) {
            throw Exception("This channel does not appear in the text")
        }

        value = result.trim()
    }

    fun removeReferencedChannel(channelNameOccurrenceIndex: Int) {
        if (referencedChannels.containsKey(channelNameOccurrenceIndex)) {
            referencedChannels.remove(channelNameOccurrenceIndex)
            return
        }

        println("This is noop. There is no channel reference occurrence at this index.")
    }

    fun removeMentionedUser(nameOccurrenceIndex: Int) {
        if (mentionedUsers.containsKey(nameOccurrenceIndex)) {
            mentionedUsers.remove(nameOccurrenceIndex)
            return
        }

        println("This is noop. There is no mention occurrence at this index.")
    }

    private fun transformMentionedUsersToSend(): JsMap<MessageMentionedUser> {
        return mentionedUsers.entries.associate { (key, value) ->
            key.toString() to MessageMentionedUser(
                value.id,
                value.name.orEmpty()
            )
        }.toJsMap()
    }

    private fun transformReferencedChannelsToSend(): JsMap<MessageReferencedChannel> {
        return referencedChannels.entries.associate { (key, value) ->
            key.toString() to MessageReferencedChannel(
                value.id,
                value.name.orEmpty(),
            )
        }.toJsMap()
    }

    fun send(options: SendTextParamsJs?): Promise<PubNub.PublishResponse> {
        @Suppress("USELESS_CAST")
        val filesList = (this.files as? Any)?.let { filesAny ->
            val filesArray = filesAny as? Array<*> ?: arrayOf(filesAny)
            filesArray.filterNotNull().map { file ->
                InputFile("", file.asDynamic().type ?: file.asDynamic().mimeType ?: "", UploadableImpl(file))
            }
        } ?: listOf()

        val mentionedUsersMap = mentionedUsers.entries.associate { (key, value) ->
            key to MessageMentionedUser(value.id, value.name.orEmpty())
        }
        val referencedChannelsMap = referencedChannels.entries.associate { (key, value) ->
            key to MessageReferencedChannel(value.id, value.name.orEmpty())
        }

        return channel.channel.sendText(
            text = value,
            meta = options?.meta?.unsafeCast<JsMap<Any>>()?.toMap(),
            shouldStore = options?.storeInHistory ?: true,
            usePost = options?.sendByPost ?: false,
            ttl = options?.ttl?.toInt(),
            mentionedUsers = mentionedUsersMap,
            referencedChannels = referencedChannelsMap,
            textLinks = _textLinks.toList(),
            quotedMessage = quotedMessage?.message,
            files = filesList,
            customPushData = options?.customPushData?.toMap(),
        ).then { result ->
            result.toPublishResponse()
        }.asPromise()
    }

    fun getHighlightedMention(selectionStart: Int): JsMap<Any?> {
        val necessaryText = value.substring(0, selectionStart - 1)
        val necessaryTextSplitBySpace = necessaryText.split(" ")
        val onlyWordsWithAt = necessaryTextSplitBySpace.filter { it.startsWith("@") }
        val lastMentionedUserInTextIndex = necessaryTextSplitBySpace.indexOfLast { it.startsWith("@") }
        val lastMentionedUserInText = necessaryTextSplitBySpace.subList(
            lastMentionedUserInTextIndex,
            necessaryTextSplitBySpace.size
        )

        val lastMentionedUser = mentionedUsers[onlyWordsWithAt.size - 1]

        if (lastMentionedUser?.name == null) {
            return mapOf(
                "mentionedUser" to null,
                "nameOccurrenceIndex" to -1
            ).toJsMap()
        }

        if (lastMentionedUserInText.size <= lastMentionedUser.name.orEmpty().split(" ").size) {
            return mapOf(
                "mentionedUser" to lastMentionedUser,
                "nameOccurrenceIndex" to onlyWordsWithAt.size - 1
            ).toJsMap()
        }

        return mapOf(
            "mentionedUser" to null,
            "nameOccurrenceIndex" to -1
        ).toJsMap()
    }

    fun addLinkedText(params: AddLinkedTextParams) {
        if (!Validator.isUrl(params.link)) {
            throw Exception("You need to insert a URL")
        }

        val linkRanges = _textLinks.flatMap { textLink ->
            range(textLink.startIndex, textLink.endIndex)
        }
        if (linkRanges.contains(params.positionInInput)) {
            throw Exception("You cannot insert a link inside another link")
        }

        onChange(value.substring(0, params.positionInInput) + params.text + value.substring(params.positionInInput))
        _textLinks.add(TextLink(params.positionInInput, params.positionInInput + params.text.length, params.link))
    }

    fun removeLinkedText(positionInInput: Int) {
        val relevantTextLinkIndex = _textLinks.indexOfFirst { textLink ->
            range(textLink.startIndex, textLink.endIndex).contains(positionInInput)
        }

        if (relevantTextLinkIndex == -1) {
            println("This operation is noop. There is no link at this position.")
            return
        }
        _textLinks.removeAt(relevantTextLinkIndex)
    }

    fun getMessagePreview(): Array<MixedTextTypedElement> { // Adjust return type as needed
        return MessageElementsUtils.getMessageElements(
            text = value,
            textLinks = _textLinks,
            mentionedUsers = mentionedUsers.mapValues { MessageMentionedUser(it.value.id, it.value.name.orEmpty()) },
            referencedChannels = referencedChannels.mapValues {
                MessageReferencedChannel(
                    it.value.id,
                    it.value.name.orEmpty()
                )
            }
        )
    }

    fun addQuote(message: MessageJs) {
        if (message.channelId != channel.id) {
            throw PubNubException("You cannot quote messages from other channels")
        }

        quotedMessage = message
    }

    fun removeQuote() {
        quotedMessage = undefined
    }
}

private class UserOfChannelReference(
    val referencesObject: MutableMap<Int, Any>,
    val differentReference: String?,
    val differentReferencePosition: Int
)
