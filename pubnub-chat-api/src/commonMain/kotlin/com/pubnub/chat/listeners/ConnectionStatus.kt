package com.pubnub.chat.listeners

import com.pubnub.api.PubNubException

/**
 * Represents the current connection state of the Chat SDK with additional error information.
 *
 * This class encapsulates both the connection status category and any associated error
 * information, providing a complete picture of the current connection state.
 *
 * @property category The current connection status category.
 * @property exception Optional exception details when an error occurs. Only present when
 *                    [category] is [ConnectionStatusCategory.PN_CONNECTION_ERROR].
 *
 * @see ConnectionStatusCategory
 *
 * Example usage:
 * ```kotlin
 * chat.addConnectionStatusListener { status ->
 *     when (status.category) {
 *         ConnectionStatusCategory.PN_CONNECTION_ONLINE -> handleConnected()
 *         ConnectionStatusCategory.PN_CONNECTION_OFFLINE -> handleDisconnected()
 *         ConnectionStatusCategory.PN_CONNECTION_ERROR -> handleError(status.exception)
 *     }
 * }
 * ```
 */
class ConnectionStatus(
    val category: ConnectionStatusCategory,
    val exception: PubNubException? = null
)
