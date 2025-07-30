package com.pubnub.chat.listeners

/**
 * Represents the connection status of a Chat SDK instance.
 */
enum class ConnectionStatusCategory {
    /**
     * The Chat SDK is connected to PubNub and subscriptions are working normally.
     * Corresponds to the PNConnectedCategory of PubNub core SDK
     */
    PN_CONNECTION_ONLINE,

    /**
     * The Chat SDK is disconnected from PubNub but no error occurred.
     * Corresponds to the PNDisconnectedCategory of PubNub core SDK
     */
    PN_CONNECTION_OFFLINE,

    /**
     * The Chat SDK lost connection to PubNub due to an error.
     * This could be due to network issues, authentication problems, or other connectivity issues.
     * Corresponds to the PNUnexpectedDisconnectCategory of PubNub core SDK
     */
    PN_CONNECTION_ERROR
}
