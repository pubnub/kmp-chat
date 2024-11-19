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
@JsName("MessageDraftV2")
class MessageDraftV2Js internal constructor(
    private val messageDraft: MessageDraftImpl,
    val config: MessageDraftConfig?,
) {
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

    fun getMessagePreview(): Array<MessageElementJs> {
        return messageDraft.getMessageElements().map { element ->
            when (element) {
                is MessageElement.Link -> when (val target = element.target) {
                    is MentionTarget.Channel -> createJsObject<MessageElementJs> {
                        this.type = "channelReference"
                        this.content = createJsObject<MessageElementPayloadJs.Channel> {
                            this.name = element.text.substring(1)
                            this.id = target.channelId
                        }
                    }
                    is MentionTarget.Url -> createJsObject<MessageElementJs> {
                        this.type = "textLink"
                        this.content = createJsObject<MessageElementPayloadJs.Link> {
                            this.text = element.text
                            this.link = target.url
                        }
                    }
                    is MentionTarget.User -> createJsObject<MessageElementJs> {
                        this.type = "mention"
                        this.content = createJsObject<MessageElementPayloadJs.User> {
                            this.name = element.text.substring(1)
                            this.id = target.userId
                        }
                    }
                }
                is MessageElement.PlainText -> createJsObject<MessageElementJs> {
                    this.type = "text"
                    this.content = createJsObject<MessageElementPayloadJs.Text> {
                        this.text = element.text
                    }
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
        messageDraft.quotedMessage = quotedMessage?.message

        return messageDraft.send(
            options?.meta?.unsafeCast<JsMap<Any>>()?.toMap(),
            options?.storeInHistory ?: true,
            options?.sendByPost ?: false,
            options?.ttl?.toInt()
        ).then { it.toPublishResponse() }.asPromise()
    }
}

external interface MessageElementJs {
    var type: String
    var content: MessageElementPayloadJs
}

external interface MessageElementPayloadJs {
    interface Text : MessageElementPayloadJs {
        var text: String
    }

    interface User : MessageElementPayloadJs {
        var name: String
        var id: String
    }

    interface Link : MessageElementPayloadJs {
        var text: String
        var link: String
    }

    interface Channel : MessageElementPayloadJs {
        var name: String
        var id: String
    }
}
