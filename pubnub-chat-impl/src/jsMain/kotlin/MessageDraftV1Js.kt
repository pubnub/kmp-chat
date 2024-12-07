// This file was auto-translated from message-draft.ts and message-element-utils.ts

@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.api.PubNubException
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.combine
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.toJsMap
import kotlin.js.Promise
import kotlin.math.abs

@JsExport
sealed class MixedTextTypedElement(val type: String) {
    data class Text(val content: TextContent) : MixedTextTypedElement("text")

    data class TextLink(val content: TextLinkContent) : MixedTextTypedElement("textLink")

    data class PlainLink(val content: PlainLinkContent) : MixedTextTypedElement("plainLink")

    data class Mention(val content: MentionContent) : MixedTextTypedElement("mention")

    data class ChannelReference(val content: ChannelReferenceContent) : MixedTextTypedElement("channelReference")
}

@JsExport
data class TextContent(val text: String)

@JsExport
data class TextLinkContent(val link: String, val text: String)

@JsExport
data class PlainLinkContent(val link: String)

@JsExport
data class MentionContent(val id: String, val name: String)

@JsExport
data class ChannelReferenceContent(val id: String, val name: String)

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

object MessageElementsUtils {
    fun getPhraseToLookFor(text: String): String? {
        val lastAtIndex = text.lastIndexOf("@")
        val charactersAfterAt = text.split("@").lastOrNull() ?: ""

        if (lastAtIndex == -1 || charactersAfterAt.length < 3) {
            return null
        }

        val splitWords = charactersAfterAt.split(" ")

        if (splitWords.size > 2) {
            return null
        }

        return splitWords[0] + (splitWords.getOrNull(1)?.let { " $it" } ?: "")
    }

    fun getChannelPhraseToLookFor(text: String): String? {
        val lastAtIndex = text.lastIndexOf("#")
        val charactersAfterHash = text.split("#").lastOrNull() ?: ""

        if (lastAtIndex == -1 || charactersAfterHash.length < 3) {
            return null
        }

        val splitWords = charactersAfterHash.split(" ")

        if (splitWords.size > 2) {
            return null
        }

        return splitWords[0] + (splitWords.getOrNull(1)?.let { " $it" } ?: "")
    }

    fun getMessageElements(
        text: String,
        mentionedUsers: Map<Int, MessageMentionedUser>,
        textLinks: List<TextLink>,
        referencedChannels: Map<Int, MessageReferencedChannel>,
    ): Array<MixedTextTypedElement> {
        var resultWithTextLinks = ""
        val indicesOfWordsWithTextLinks = mutableListOf<WordWithTextLink>()

        val textLinkRanges = textLinks.map { textLink ->
            (textLink.startIndex..textLink.endIndex step 1).toList()
        }
        val allIndices = textLinkRanges.flatten()
        val startIndices = textLinkRanges.map { it.first() }
        val endIndices = textLinkRanges.map { it.last() }
        var spacesSoFar = 0

        text.forEachIndexed { i, letter ->
            if (letter == ' ') {
                spacesSoFar++
            }
            if (i in startIndices) {
                val relevantIndex = startIndices.indexOf(i)
                val substring = text.substring(i, endIndices[relevantIndex])

                resultWithTextLinks += substring

                indicesOfWordsWithTextLinks.add(
                    WordWithTextLink(
                        start = spacesSoFar,
                        end = spacesSoFar + substring.split(" ").size,
                        link = textLinks[relevantIndex].link,
                        substring = substring
                    )
                )
                return@forEachIndexed
            }
            if (i in allIndices && i !in endIndices) {
                return@forEachIndexed
            }
            resultWithTextLinks += letter
        }

        var counter = 0
        var channelCounter = 0
        val indicesToSkip = mutableListOf<Int>()
        val channelIndicesToSkip = mutableListOf<Int>()

        val splitText = resultWithTextLinks.split(" ")
        val arrayOfTextElements = mutableListOf<MixedTextTypedElement>()

        splitText.forEachIndexed { index, word ->
            if (!word.startsWith("@") && !word.startsWith("#")) {
                if (index in indicesToSkip || index in channelIndicesToSkip) {
                    return@forEachIndexed
                }

                val foundTextLink = indicesOfWordsWithTextLinks.find { it.start == index }

                if (foundTextLink != null) {
                    val substring = splitText.slice(foundTextLink.start until foundTextLink.end).joinToString(" ")
                    val additionalPunctuation = substring.replace(foundTextLink.substring, "")

                    arrayOfTextElements.add(
                        MixedTextTypedElement.TextLink(
                            TextLinkContent(
                                link = foundTextLink.link,
                                text = foundTextLink.substring
                            )
                        )
                    )
                    if (additionalPunctuation.isNotEmpty()) {
                        arrayOfTextElements.add(
                            MixedTextTypedElement.Text(
                                TextContent(additionalPunctuation)
                            )
                        )
                    }
                    arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(" ")))
                    indicesToSkip.addAll((index until index + substring.split(" ").size).toList())
                    return@forEachIndexed
                }

                if (Validator.isUrl(word)) {
                    val lastCharacter = word.lastOrNull()?.toString() ?: ""
                    if (lastCharacter in listOf("!", "?", ".", ",")) {
                        arrayOfTextElements.add(
                            MixedTextTypedElement.PlainLink(
                                PlainLinkContent(word.dropLast(1))
                            )
                        )
                        arrayOfTextElements.add(
                            MixedTextTypedElement.Text(
                                TextContent(lastCharacter)
                            )
                        )
                        arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(" ")))
                    } else {
                        arrayOfTextElements.add(
                            MixedTextTypedElement.PlainLink(
                                PlainLinkContent(word)
                            )
                        )
                        arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(" ")))
                    }
                    return@forEachIndexed
                }

                if (word.isNotEmpty()) {
                    arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(word)))
                    arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(" ")))
                }
            } else if (word.startsWith("@")) {
                val mentionFound = counter in mentionedUsers

                if (!mentionFound) {
                    counter++
                    arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(word)))
                    arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(" ")))
                } else {
                    val userId = mentionedUsers[counter]!!.id
                    val userName = mentionedUsers[counter]!!.name
                    val userNameWords = userName.split(" ")

                    var additionalPunctuationCharacters = ""

                    if (userNameWords.size > 1) {
                        indicesToSkip.addAll((index until index + userNameWords.size).toList())
                        additionalPunctuationCharacters = splitText[indicesToSkip.last()]
                            .replace(userNameWords.last(), "")
                    } else {
                        additionalPunctuationCharacters = word.replace("@", "").replace(userName, "")
                    }
                    if (additionalPunctuationCharacters.isNotEmpty()) {
                        additionalPunctuationCharacters += " "
                    }
                    if (additionalPunctuationCharacters.isEmpty()) {
                        additionalPunctuationCharacters = " "
                    }

                    counter++
                    arrayOfTextElements.add(
                        MixedTextTypedElement.Mention(
                            MentionContent(
                                id = userId,
                                name = userName
                            )
                        )
                    )
                    arrayOfTextElements.add(
                        MixedTextTypedElement.Text(
                            TextContent(additionalPunctuationCharacters)
                        )
                    )
                }
            } else {
                val channelReferenceFound = channelCounter in referencedChannels

                if (!channelReferenceFound) {
                    channelCounter++
                    arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(word)))
                    arrayOfTextElements.add(MixedTextTypedElement.Text(TextContent(" ")))
                } else {
                    val channelId = referencedChannels[channelCounter]!!.id
                    val channelName = referencedChannels[channelCounter]!!.name
                    val channelNameWords = channelName.split(" ")

                    var additionalPunctuationCharacters = ""

                    if (channelNameWords.size > 1) {
                        channelIndicesToSkip.addAll((index until index + channelNameWords.size).toList())
                        additionalPunctuationCharacters = splitText[channelIndicesToSkip.last()]
                            .replace(channelNameWords.last(), "")
                    } else {
                        additionalPunctuationCharacters = word.replace("#", "").replace(channelName, "")
                    }
                    if (additionalPunctuationCharacters.isNotEmpty()) {
                        additionalPunctuationCharacters += " "
                    }
                    if (additionalPunctuationCharacters.isEmpty()) {
                        additionalPunctuationCharacters = " "
                    }

                    channelCounter++
                    arrayOfTextElements.add(
                        MixedTextTypedElement.ChannelReference(
                            ChannelReferenceContent(
                                id = channelId,
                                name = channelName
                            )
                        )
                    )
                    arrayOfTextElements.add(
                        MixedTextTypedElement.Text(
                            TextContent(additionalPunctuationCharacters)
                        )
                    )
                }
            }
        }

        if (arrayOfTextElements.lastOrNull() is MixedTextTypedElement.Text) {
            val lastTextElement = arrayOfTextElements.last() as MixedTextTypedElement.Text
            arrayOfTextElements[arrayOfTextElements.lastIndex] =
                lastTextElement.copy(content = lastTextElement.content.copy(text = lastTextElement.content.text.trim()))
            lastTextElement.copy(content = lastTextElement.content.copy(text = lastTextElement.content.text.trim()))
        }
        if (arrayOfTextElements.lastOrNull() is MixedTextTypedElement.Text &&
            (arrayOfTextElements.last() as MixedTextTypedElement.Text).content.text in listOf(" ", "")
        ) {
            arrayOfTextElements.removeLast()
        }

        return arrayOfTextElements.fold(listOf()) { acc: List<MixedTextTypedElement>, curr: MixedTextTypedElement ->
            val previousObject = acc.lastOrNull()
            if (curr is MixedTextTypedElement.Text && previousObject is MixedTextTypedElement.Text) {
                acc.dropLast(1) + MixedTextTypedElement.Text(
                    TextContent(previousObject.content.text + curr.content.text)
                )
            } else {
                acc + curr
            }
        }.toTypedArray()
    }
}

data class WordWithTextLink(
    val start: Int,
    val end: Int,
    val substring: String,
    val link: String,
)

external interface AddLinkedTextParams {
    val text: String
    val link: String
    val positionInInput: Int
}

fun range(start: Int, stop: Int, step: Int = 1): List<Int> {
    return (start..stop step step).toList()
}

@JsExport
@JsName("MessageDraft")
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

    fun send(options: PubNub.PublishParameters?): Promise<Any> {
        val sendTextOptions = createJsObject<SendTextOptionParams> {
            this.files = this@MessageDraftV1Js.files
            this.mentionedUsers = transformMentionedUsersToSend()
            this.referencedChannels = transformReferencedChannelsToSend()
            this.textLinks = this@MessageDraftV1Js._textLinks.toTypedArray()
            this.quotedMessage = this@MessageDraftV1Js.quotedMessage
        }.combine(options?.unsafeCast<JsMap<Any>>())

        return channel.sendText(value, sendTextOptions)
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
