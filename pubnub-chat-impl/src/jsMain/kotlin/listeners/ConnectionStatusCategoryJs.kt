@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package listeners

import com.pubnub.chat.listeners.ConnectionStatusCategory

@JsExport
@JsName("ConnectionStatusCategory")
class ConnectionStatusCategoryJs internal constructor(
    private val category: ConnectionStatusCategory
) {
    @JsName("value")
    val value: String = category.name

    override fun toString(): String = category.name

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ConnectionStatusCategoryJs) {
            return false
        }
        return category == other.category
    }

    override fun hashCode(): Int = category.hashCode()

    companion object {
        @JsStatic
        @JsName("PN_CONNECTION_ONLINE")
        val PN_CONNECTION_ONLINE = ConnectionStatusCategoryJs(ConnectionStatusCategory.PN_CONNECTION_ONLINE)

        @JsStatic
        @JsName("PN_CONNECTION_OFFLINE")
        val PN_CONNECTION_OFFLINE = ConnectionStatusCategoryJs(ConnectionStatusCategory.PN_CONNECTION_OFFLINE)

        @JsStatic
        @JsName("PN_CONNECTION_ERROR")
        val PN_CONNECTION_ERROR = ConnectionStatusCategoryJs(ConnectionStatusCategory.PN_CONNECTION_ERROR)
    }
}
