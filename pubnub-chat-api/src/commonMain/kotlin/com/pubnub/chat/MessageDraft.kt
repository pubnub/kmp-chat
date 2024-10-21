package com.pubnub.chat

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.chat.types.InputFile
import com.pubnub.kmp.PNFuture

/**
 * MessageDraft is an object that refers to a single message that has not been published yet.
 *
 * It contains mutable state (the draft of the message text) and helps with editing tasks such as adding user mentions,
 * channel references and links.
 */
interface MessageDraft {
    /**
     * The [Channel] where this [MessageDraft] will be published.
     */
    val channel: Channel

    /**
     * The scope for searching for suggested users - either [UserSuggestionSource.GLOBAL] or [UserSuggestionSource.CHANNEL].
     */
    val userSuggestionSource: UserSuggestionSource

    /**
     * Whether modifying the message text triggers the typing indicator on [channel].
     */
    val isTypingIndicatorTriggered: Boolean

    /**
     * The limit on the number of users returned when searching for users to mention.
     */
    val userLimit: Int

    /**
     * The limit on the number of channels returned when searching for channels to reference.
     */
    val channelLimit: Int

    /**
     * Can be used to set a [Message] to quote when sending this [MessageDraft].
     */
    var quotedMessage: Message?

    /**
     * Can be used to attach files to send with this [MessageDraft].
     */
    val files: MutableList<InputFile>

    /**
     * Add a [MessageDraftStateListener] to listen for changes to the contents of this [MessageDraft], as well as
     * to retrieve the current mention suggestions for users and channels (e.g. when the message draft contains
     * "... @name ..." or "... #chann ...")
     *
     * @param callback the [MessageDraftStateListener] that will receive the most current message elements list and
     * suggestions list.
     */
    fun addMessageElementsListener(listener: MessageDraftStateListener)

    /**
     * Remove the given [MessageDraftStateListener] from active listeners.
     */
    fun removeMessageElementsListener(listener: MessageDraftStateListener)

    /**
     * Insert some text into the [MessageDraft] text at the given offset.
     *
     * @param offset the position from the start of the message draft where insertion will occur
     * @param text the text to insert at the given offset
     */
    fun insertText(offset: Int, text: String)

    /**
     * Remove a number of characters from the [MessageDraft] text at the given offset.
     *
     * @param offset the position from the start of the message draft where removal will occur
     * @param length the number of characters to remove, starting at the given offset
     */
    fun removeText(offset: Int, length: Int)

    /**
     * Insert mention into the [MessageDraft] according to [SuggestedMention.offset], [SuggestedMention.replaceFrom] and
     * [SuggestedMention.target].
     *
     * The [SuggestedMention] must be up to date with the message text, that is: [SuggestedMention.replaceFrom] must
     * match the message draft at position [SuggestedMention.replaceFrom], otherwise an exception will be thrown.
     *
     * @param mention a [SuggestedMention] that can be obtained from [MessageDraftStateListener]
     * @param text the text to replace [SuggestedMention.replaceFrom] with. [SuggestedMention.replaceTo] can be used for example.
     */
    fun insertSuggestedMention(mention: SuggestedMention, text: String)

    /**
     * Add a mention to a user, channel or link specified by [target] at the given offset.
     *
     * @param offset the start of the mention
     * @param length the number of characters (length) of the mention
     * @param target the target of the mention, e.g. [MentionTarget.User], [MentionTarget.Channel] or [MentionTarget.Url]
     */
    fun addMention(offset: Int, length: Int, target: MentionTarget)

    /**
     * Remove a mention starting at the given offset, if any.
     *
     * @param offset the start of the mention to remove
     */
    fun removeMention(offset: Int)

    /**
     * Update the whole message draft text with a new value.
     *
     * Internally [MessageDraft] will try to calculate the most
     * optimal set of insertions and removals that will convert the current text to the provided [text], in order to
     * preserve any mentions. This is a best effort operation, and if any mention text is found to be modified,
     * the mention will be invalidated and removed.
     */
    fun update(text: String)

    /**
     * Send the [MessageDraft], along with its [files] and [quotedMessage] if any, on the [channel].
     *
     * @param meta Publish additional details with the request.
     * @param shouldStore If true, the messages are stored in Message Persistence if enabled in Admin Portal.
     * @param usePost Use HTTP POST
     * @param ttl Defines if / how long (in hours) the message should be stored in Message Persistence.
     * If shouldStore = true, and ttl = 0, the message is stored with no expiry time.
     * If shouldStore = true and ttl = X, the message is stored with an expiry time of X hours.
     * If shouldStore = false, the ttl parameter is ignored.
     * If ttl is not specified, then the expiration of the message defaults back to the expiry value for the keyset.
     *
     * @return [PNFuture] containing [PNPublishResult] that holds the timetoken of the sent message.
     */
    fun send(
        meta: Map<String, Any>? = null,
        shouldStore: Boolean = true,
        usePost: Boolean = false,
        ttl: Int? = null,
    ): PNFuture<PNPublishResult>

    /**
     * Enum describing the source for getting user suggestions for mentions.
     */
    enum class UserSuggestionSource {
        /**
         * Search for users globally.
         */
        GLOBAL,

        /**
         * Search only for users that are members of this channel.
         */
        CHANNEL
    }
}

/**
 * Defines the target of the mention attached to a [MessageDraft].
 */
sealed interface MentionTarget {
    /**
     * Mention a user identified by [userId].
     */
    data class User(val userId: String) : MentionTarget

    /**
     * Reference a channel identified by [channelId].
     */
    data class Channel(val channelId: String) : MentionTarget

    /**
     * Link to [url].
     */
    data class Url(val url: String) : MentionTarget
}

/**
 * Part of a [Message] or [MessageDraft] content.
 */
sealed interface MessageElement {
    /**
     * The literal text contained in this [MessageElement]. This is what the user should see when reading or composing the message.
     */
    val text: String

    /**
     * Element that contains plain text, without any additional metadata or links.
     */
    data class PlainText(override val text: String) : MessageElement

    /**
     * Element that has attached metadata, specifically a mention described by [target].
     */
    data class Link(override val text: String, val target: MentionTarget) : MessageElement
}

/**
 * A potential mention suggestion received from [MessageDraftStateListener].
 *
 * It can be used with [MessageDraft.insertSuggestedMention] to accept the suggestion and attach a mention to a message draft.
 *
 * @param offset the offset where the mention starts
 * @param replaceFrom the original text at the [offset] in the message draft text
 * @param replaceTo the suggested replacement for the [replaceFrom] text, e.g. the user's full name
 * @param target the target of the mention, such as a user, channel or URL
 */
class SuggestedMention(val offset: Int, val replaceFrom: String, val replaceTo: String, val target: MentionTarget)

/**
 * A listener that can be used with [MessageDraft.addMessageElementsListener] to listen for changes to the message draft
 * text and get current mention suggestions.
 */
fun interface MessageDraftStateListener {
    fun onChange(messageElements: List<MessageElement>, suggestedMentions: PNFuture<List<SuggestedMention>>)
}

/**
 * Utility function for filtering suggestions for a specific position in the message draft text.
 *
 * @param position the cursor position in the message draft text
 */
fun List<SuggestedMention>.getSuggestionsFor(position: Int): List<SuggestedMention> =
    filter { it.offset <= position }
        .filter { suggestedMention ->
            position in suggestedMention.offset..(suggestedMention.offset + suggestedMention.replaceFrom.length)
        }
