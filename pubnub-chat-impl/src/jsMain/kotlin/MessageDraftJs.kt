@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.MentionTarget
import com.pubnub.chat.MessageElement
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.types.InputFile
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.UploadableImpl
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toMap
import kotlin.js.Promise

@JsExport
@JsName("MessageDraft")
class MessageDraftJs internal constructor(
    private val messageDraft: MessageDraftImpl,
    val config: MessageDraftConfig?,
) {
    val value: String get() = messageDraft.value.toString()
    var quotedMessage: MessageJs? get() = messageDraft.quotedMessage?.asJs()
        set(value) {
            messageDraft.quotedMessage = value?.message
        }
    var files: Any? = null

    fun onChange(text: String) {
        messageDraft.update(text)
    }

    fun addQuote(message: MessageJs) {
        quotedMessage = message
    }

    fun removeQuote() {
        quotedMessage = null
    }

    fun addLinkedText(params: dynamic) {
        val text: String = params.text
        val link: String = params.link
        val offset: Int = params.positionInInput
        messageDraft.insertText(offset, text)
        messageDraft.addMention(offset, text.length, MentionTarget.Url(link))
    }

    fun removeLinkedText(positionOnInput: Int) {
        messageDraft.removeMention(positionOnInput)
    }

    fun getMessagePreview(): Array<MessageElementJs> {
        return messageDraft.getMessageElements().map { element ->
            when (element) {
                is MessageElement.Link -> when (val target = element.target) {
                    is MentionTarget.Channel -> createJsObject<MessageElementJs.Channel> {
                        this.type = "channelReference"
                        this.name = element.text
                        this.id = target.channelId
                    }
                    is MentionTarget.Url -> createJsObject<MessageElementJs.Link> {
                        this.type = "textLink"
                        this.text = element.text
                        this.link = target.url
                    }
                    is MentionTarget.User -> createJsObject<MessageElementJs.User> {
                        this.type = "mention"
                        this.name = element.text
                        this.id = target.userId
                    }
                }
                is MessageElement.PlainText -> createJsObject<MessageElementJs.Text> {
                    this.type = "text"
                    this.text = element.text
                }
            }
        }.toTypedArray()
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

        return messageDraft.send(
            options?.meta?.unsafeCast<JsMap<Any>>()?.toMap(),
            options?.storeInHistory ?: true,
            options?.sendByPost ?: false,
            options?.ttl?.toInt()
        ).then { result ->
            createJsObject<PubNub.PublishResponse> { timetoken = result.timetoken.toString() }
        }.asPromise()
    }
}

external interface MessageElementJs {
    var type: String

    interface Text : MessageElementJs {
        var text: String
    }

    interface User : MessageElementJs {
        var name: String
        var id: String
    }

    interface Link : MessageElementJs {
        var text: String
        var link: String
    }

    interface Channel : MessageElementJs {
        var name: String
        var id: String
    }
}
