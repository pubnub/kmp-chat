package com.pubnub.chat.listeners

/**
 * Represents the connection status of a Chat SDK instance.
 *
 * This enum class provides a simplified view of the underlying PubNub connection states,
 * mapping complex PubNub status categories to three main states that are relevant for
 * chat applications.
 *
 * The status categories are used to:
 * - Monitor connection health
 * - Handle reconnection logic
 * - Provide user feedback about connection state
 *
 * @see ConnectionStatus
 */
enum class ConnectionStatusCategory {
    /**
     * The Chat SDK is connected to PubNub and subscriptions are working normally.
     *
     * This status indicates that:
     * - The connection to PubNub servers is established
     * - Messages can be sent and received
     * - All subscribed channels are active
     *
     * Corresponds to [PNStatusCategory.PNConnectedCategory] in the PubNub core SDK.
     */
    PN_CONNECTION_ONLINE,

    /**
     * The Chat SDK is disconnected from PubNub but no error occurred.
     *
     * This status indicates a normal disconnection, which can happen due to:
     * - Explicit disconnect call ([Chat.disconnectSubscriptions])
     * - Clean connection teardown
     * - Intentional connection closure
     *
     * Corresponds to [PNStatusCategory.PNDisconnectedCategory] in the PubNub core SDK.
     */
    PN_CONNECTION_OFFLINE,

    /**
     * The Chat SDK lost connection to PubNub due to an error.
     *
     * This status indicates an unexpected disconnection, which can occur due to:
     * - Network connectivity issues
     * - Authentication failures
     * - Server errors
     * - Timeout issues
     *
     * You could configure retry configuration available in underlying the core PubNub SDK by defining it
     * in PNConfiguration when initializing the Chat SDK [Chat.init].
     *
     * Corresponds to [PNStatusCategory.PNUnexpectedDisconnectCategory] and
     * [PNStatusCategory.PNConnectionError] in the PubNub core SDK.
     */
    PN_CONNECTION_ERROR
}
