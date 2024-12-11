@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.MentionTarget
import com.pubnub.chat.MessageDraftChangeListener
import com.pubnub.chat.MessageElement
import com.pubnub.chat.SuggestedMention
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.types.InputFile
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.UploadableImpl
import com.pubnub.kmp.then
import com.pubnub.kmp.toMap
import kotlin.js.Promise

@JsExport
@JsName("MessageDraftV2")
class MessageDraftV2Js internal constructor(
    private val chat: ChatJs,
    private val messageDraft: MessageDraftImpl,
    val config: MessageDraftConfig,
) {
    val channel: ChannelJs get() = messageDraft.channel.asJs(chat)
    val value: String get() = messageDraft.value.toString()
    var quotedMessage: MessageJs? = null
    var files: Any? = null

    fun addQuote(message: MessageJs) {
        quotedMessage = message
    }

    fun removeQuote() {
        quotedMessage = null
    }

    fun addLinkedText(params: AddLinkedTextParams) {
        val text: String = params.text
        val link: String = params.link
        val offset: Int = params.positionInInput
        messageDraft.insertText(offset, text)
        messageDraft.addMention(offset, text.length, MentionTarget.Url(link))
    }

    fun removeLinkedText(positionOnInput: Int) {
        messageDraft.removeMention(positionOnInput)
    }

    fun getMessagePreview(): Array<MixedTextTypedElement> {
        return messageDraft.getMessageElements().toJs()
    }

    fun send(options: PubNub.PublishParameters?): Promise<PubNub.PublishResponse> {
        val filesArray = files?.let {
            it as? Array<*> ?: arrayOf(it)
        } ?: arrayOf()

        filesArray.forEach { file: dynamic ->
            val type = file.type ?: file.mimeType
            val name = file.name
            messageDraft.files.add(InputFile(name ?: "", type ?: "", UploadableImpl(file)))
        }
        messageDraft.quotedMessage = quotedMessage?.message

        return messageDraft.send(
            options?.meta?.unsafeCast<JsMap<Any>>()?.toMap(),
            options?.storeInHistory ?: true,
            options?.sendByPost ?: false,
            options?.ttl?.toInt()
        ).then { it.toPublishResponse() }.asPromise()
    }

    fun addChangeListener(listener: (MessageDraftState) -> Unit) {
        messageDraft.addChangeListener(MessageDraftListenerJs(listener))
    }

    fun removeChangeListener(listener: (MessageDraftState) -> Unit) {
        messageDraft.removeChangeListener(MessageDraftListenerJs(listener))
    }

    fun insertText(offset: Int, text: String) = messageDraft.insertText(offset, text)

    fun removeText(offset: Int, length: Int) = messageDraft.removeText(offset, length)

    fun insertSuggestedMention(mention: SuggestedMentionJs, text: String) {
        return messageDraft.insertSuggestedMention(
            SuggestedMention(
                mention.offset,
                mention.replaceFrom,
                mention.replaceWith,
                when (mention.type) {
                    TYPE_MENTION -> MentionTarget.User(mention.target)
                    TYPE_CHANNEL_REFERENCE -> MentionTarget.Channel(mention.target)
                    TYPE_TEXT_LINK -> MentionTarget.Url(mention.target)
                    else -> throw IllegalStateException("Unknown target type")
                }
            ),
            text
        )
    }

    fun addMention(offset: Int, length: Int, mentionType: String, mentionTarget: String) {
        return messageDraft.addMention(
            offset,
            length,
            when (mentionType) {
                TYPE_MENTION -> MentionTarget.User(mentionTarget)
                TYPE_CHANNEL_REFERENCE -> MentionTarget.Channel(mentionTarget)
                TYPE_TEXT_LINK -> MentionTarget.Url(mentionTarget)
                else -> throw IllegalStateException("Unknown target type")
            }
        )
    }

    fun removeMention(offset: Int) = messageDraft.removeMention(offset)

    fun update(text: String) = messageDraft.update(text)
}

@JsExport
class MessageDraftState internal constructor(
    val messageElements: Array<MixedTextTypedElement>,
    suggestedMentionsFuture: PNFuture<List<SuggestedMention>>
) {
    val suggestedMentions: Promise<Array<SuggestedMentionJs>> by lazy {
        suggestedMentionsFuture.then {
            it.map {
                SuggestedMentionJs(
                    it.offset,
                    it.replaceFrom,
                    it.replaceWith,
                    when (it.target) {
                        is MentionTarget.Channel -> TYPE_CHANNEL_REFERENCE
                        is MentionTarget.Url -> TYPE_TEXT_LINK
                        is MentionTarget.User -> TYPE_MENTION
                    },
                    when (val link = it.target) {
                        is MentionTarget.Channel -> link.channelId
                        is MentionTarget.Url -> link.url
                        is MentionTarget.User -> link.userId
                    }
                )
            }.toTypedArray()
        }.asPromise()
    }
}

data class MessageDraftListenerJs(val listener: (MessageDraftState) -> Unit) : MessageDraftChangeListener {
    override fun onChange(
        messageElements: List<MessageElement>,
        suggestedMentions: PNFuture<List<SuggestedMention>>,
    ) {
        listener(
            MessageDraftState(
                messageElements.toJs(),
                suggestedMentions
            )
        )
    }
}

@JsExport
@JsName("SuggestedMention")
class SuggestedMentionJs(
    val offset: Int,
    val replaceFrom: String,
    val replaceWith: String,
    val type: String,
    val target: String,
)

private const val TYPE_CHANNEL_REFERENCE = "channelReference"
private const val TYPE_TEXT_LINK = "textLink"
private const val TYPE_MENTION = "mention"
private const val TYPE_TEXT = "text"

fun List<MessageElement>.toJs() = map { element ->
    when (element) {
        is MessageElement.Link -> when (val target = element.target) {
            is MentionTarget.Channel -> MixedTextTypedElement.ChannelReference(
                ChannelReferenceContent(target.channelId, element.text.substring(1))
            )
            is MentionTarget.Url -> MixedTextTypedElement.TextLink(TextLinkContent(target.url, element.text))
            is MentionTarget.User -> MixedTextTypedElement.Mention(MentionContent(target.userId, element.text.substring(1)))
        }
        is MessageElement.PlainText -> MixedTextTypedElement.Text(TextContent(element.text))
    }
}.toTypedArray()
