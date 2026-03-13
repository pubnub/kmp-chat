@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.TextLink

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

external interface AddLinkedTextParams {
    val text: String
    val link: String
    val positionInInput: Int
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

                if (isUrl(word)) {
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
                                name = "@$userName"
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
                                name = "#$channelName"
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

    private val validProtocols = listOf("http://", "https://", "www.")
    private val urlRegex =
        """(https?:\/\/(?:www\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|www\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|https?:\/\/(?:www\.|(?!www))[a-zA-Z0-9]+\.[^\s]{2,}|www\.[a-zA-Z0-9]+\.[^\s]{2,})""".toRegex(
            RegexOption.IGNORE_CASE
        )

    private fun isUrl(potentialUrl: String): Boolean {
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

private data class WordWithTextLink(
    val start: Int,
    val end: Int,
    val substring: String,
    val link: String,
)
