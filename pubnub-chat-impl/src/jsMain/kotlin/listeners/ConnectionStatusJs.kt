@file:OptIn(ExperimentalJsExport::class)

package listeners

import com.pubnub.api.PubNubException
import com.pubnub.chat.listeners.ConnectionStatus

@JsExport
@JsName("ConnectionStatus")
class ConnectionStatusJs internal constructor(
    private val status: ConnectionStatus
) {
    @JsName("category")
    val category: ConnectionStatusCategoryJs = ConnectionStatusCategoryJs(status.category)

    @JsName("exception")
    val exception: PubNubException? = status.exception
}
