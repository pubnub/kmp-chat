@file:OptIn(ExperimentalJsExport::class)

@JsExport
@JsName("Event")
class EventJs(
    val chat: ChatJs,
    val timetoken: String,
    val type: String,
    val payload: dynamic,
    val channelId: String,
    val userId: String,
)
