import com.pubnub.chat.internal.MessageDraftImpl

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("QuotedMessage")
class QuotedMessageJs(
    val timetoken: String,
    val text: String,
    val userId: String,
) {
    fun getMessageElements(): Array<MixedTextTypedElement> {
        return MessageDraftImpl.getMessageElements(text).toJs()
    }
}
